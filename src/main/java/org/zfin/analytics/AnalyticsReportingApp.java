package org.zfin.analytics;

import com.google.analytics.data.v1beta.*;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.api.services.analyticsreporting.v4.AnalyticsReportingScopes;
import com.google.api.services.analyticsreporting.v4.AnalyticsReporting;

//import com.google.api.services.analyticsreporting.v4.model.*;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import com.google.api.client.http.HttpRequestInitializer;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;


public class AnalyticsReportingApp {
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String KEY_FILE_LOCATION = "credentials.json";
    private final String configFilename;
    private Config config;

    public AnalyticsReportingApp(String configFilename) {
        this.configFilename = configFilename;
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar <jarfile> <report-config.json>");
            System.exit(1);
        }

        System.out.println("Starting Analytics Reporting App");
        AnalyticsReportingApp analyticsReportingApp = new AnalyticsReportingApp(args[0]);
        analyticsReportingApp.run();
    }

    public void run() {
        try {
            loadConfigJson();
            runReportAndWriteCsvFiles();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Initializes an Analytics Reporting API V4 service object.
     *
     * @return An authorized Analytics Reporting API V4 service object.
     * @throws IOException
     * @throws GeneralSecurityException
     */
    private AnalyticsReporting initializeAnalyticsReporting() throws GeneralSecurityException, IOException {

        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleCredential credential = GoogleCredential
                .fromStream(new FileInputStream(KEY_FILE_LOCATION))
                .createScoped(AnalyticsReportingScopes.all());

        // Construct the Analytics Reporting service object.
        return new AnalyticsReporting.Builder(httpTransport, JSON_FACTORY, setHttpTimeout(credential))
                .setApplicationName(config.applicationName).build();
    }

    private static HttpRequestInitializer setHttpTimeout(final HttpRequestInitializer requestInitializer) {
        return new HttpRequestInitializer() {
            @Override
            public void initialize(HttpRequest httpRequest) throws IOException {
                requestInitializer.initialize(httpRequest);
                httpRequest.setConnectTimeout(3 * 60000);  // 3 minutes connect timeout
                httpRequest.setReadTimeout(3 * 60000);  // 3 minutes read timeout
            }};
    }

    /**
     * Queries the Analytics Reporting API V4.
     */
    private void runReportAndWriteCsvFiles() {
        RunReportRequest.Builder runReportRequestBuilder = RunReportRequest.newBuilder();
        System.out.println("Getting report for " + config.reportName);
        System.out.println("Property ID: " + config.propertyId);
        runReportRequestBuilder.setProperty("properties/" + config.propertyId);

        // DateRange
        runReportRequestBuilder.addDateRanges(DateRange.newBuilder().setStartDate(config.startDate).setEndDate(config.endDate));

        // Configure Metrics
        config.metrics.forEach(metricName -> {
            runReportRequestBuilder.addMetrics(Metric.newBuilder().setName(metricName));
        });

        // Configure Dimensions
        config.dimensions.forEach(dimensionName -> {
            runReportRequestBuilder.addDimensions(Dimension.newBuilder().setName(dimensionName));
        });

        // Configure the sort order
        config.sort.forEach(orderFieldName -> {
//            runReportRequestBuilder.addOrderBys(OrderBy.newBuilder().setMetric(OrderBy.MetricOrderBy.newBuilder().setMetricName(orderFieldName)).build());
            runReportRequestBuilder.addOrderBys(OrderBy.newBuilder().setDimension(OrderBy.DimensionOrderBy.newBuilder().setDimensionName(orderFieldName)).build());
        });

        runReportRequestBuilder.setLimit(config.limit);

        RunReportRequest request = runReportRequestBuilder.build();

        try(BetaAnalyticsDataClient service = BetaAnalyticsDataClient.create()) {
            RunReportResponse response = service.runReport(request);

            int totalRows = response.getRowCount();
            int rowsInResponse = response.getRowsCount();

            System.out.println("Total rows: " + totalRows);
            System.out.println("Rows in response: " + rowsInResponse);

            int pageCount = 0;
            while (rowsInResponse < totalRows) {
                request = runReportRequestBuilder.setOffset(rowsInResponse).build();
                response = service.runReport(request);
                rowsInResponse += response.getRowsCount();
                pageCount++;
                System.out.println("Getting page " + pageCount + " for " + config.reportName);
                writeResponseToCsvFile(response.getRowsList());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeResponseToCsvFile(List<Row> response) {
        //write analytics reports to csv files
        try {
            int fileSuffix = 0;
            String directoryName = new File(this.configFilename).getParent();
            if (new File(directoryName + "/csv").exists()) {
                directoryName = directoryName + "/csv";
            }

            String filename = directoryName + "/" + config.reportName + "-" + fileSuffix + ".csv";
            //while file exists, increment suffix
            while(new File(filename).exists()) {
                fileSuffix++;
                filename = directoryName + "/" + config.reportName + "-" + fileSuffix + ".csv";
            }
            System.out.println("Writing report to " + filename);

            //write headers
            List<String> headers = new ArrayList<>();
            config.metrics.forEach(metric -> headers.add(metric));
            config.dimensions.forEach(dimension -> headers.add(dimension));

            Path path = Paths.get( filename );
            BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
            CSVPrinter csvWriter = new CSVPrinter(writer, CSVFormat.DEFAULT
                    .withHeader( headers.toArray(new String[0]) ));

            for (Row row: response) {
                List<DimensionValue> dimensions = row.getDimensionValuesList();
                List<MetricValue> metrics = row.getMetricValuesList();
                List<String> rowData = new ArrayList<>();

                for (int j = 0; j < metrics.size(); j++) {
                    MetricValue value = metrics.get(j);
                    rowData.add(value.getValue());
                }

                for (int i = 0; i < dimensions.size(); i++) {
                    DimensionValue value = dimensions.get(i);
                    rowData.add(value.getValue());
                }

                csvWriter.printRecord(rowData);
            }
            csvWriter.flush();
            csvWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadConfigJson() throws Exception {
        //filename of the config json file
        String filename = configFilename;

        //read the config json file with gson
        try {
            JsonReader reader = new JsonReader(new FileReader(filename));
            Gson gson = new Gson();
            config = gson.fromJson(reader, Config.class);
        } catch (FileNotFoundException e) {
            System.err.println("Config file not found");
            throw new Exception("Config file not found");
        }
    }

}
