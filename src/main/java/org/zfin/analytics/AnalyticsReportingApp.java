package org.zfin.analytics;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.api.services.analyticsreporting.v4.AnalyticsReportingScopes;
import com.google.api.services.analyticsreporting.v4.AnalyticsReporting;

import com.google.api.services.analyticsreporting.v4.model.*;
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
        System.out.println("Dying");
        System.exit(0);


        AnalyticsReportingApp analyticsReportingApp = new AnalyticsReportingApp(args[0]);
        analyticsReportingApp.run();
    }

    public void run() {
        try {
            loadConfigJson();
            AnalyticsReporting service = initializeAnalyticsReporting();

            System.out.println("Getting report for " + config.reportName);
            GetReportsResponse response = getReport(service);
            writeResponseToCsvFile(response);

            pullNextPage(service, response);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void pullNextPage(AnalyticsReporting service, GetReportsResponse response) throws IOException {
        if (response.getReports().get(0).getNextPageToken() != null) {
            //sleep for 15 seconds to avoid rate limit
            try {
                System.out.println("Sleeping for 15 seconds to avoid rate limit");
                Thread.sleep(15000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            config.nextPageToken = response.getReports().get(0).getNextPageToken();

            System.out.println("Getting next page for " + config.reportName + " with token " + config.nextPageToken);
            GetReportsResponse nextPageResponse = getReport(service);
            writeResponseToCsvFile(nextPageResponse);
            pullNextPage(service, nextPageResponse);
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
     *
     * @param service An authorized Analytics Reporting API V4 service object.
     * @return GetReportResponse The Analytics Reporting API V4 response.
     * @throws IOException
     */
    private GetReportsResponse getReport(AnalyticsReporting service) throws IOException {
        // Create the DateRange object.
        DateRange dateRange = new DateRange();
        dateRange.setStartDate(config.startDate);
        dateRange.setEndDate(config.endDate);

        // Create the Metrics object.
        List<Metric> metrics = new ArrayList<>();
        config.metrics.forEach(metricName -> {
            Metric metric = new Metric();
            metric.setExpression(metricName);
            metric.setAlias(metricName.replace("ga:", ""));
            metrics.add(metric);
        });

        // Create the Dimension object.
        List<Dimension> dimensions = new ArrayList<>();
        config.dimensions.forEach(dimensionName -> {
            Dimension dimension = new Dimension();
            dimension.setName(dimensionName);
            dimensions.add(dimension);
        });

        // Create the OrderBy object.
        List<OrderBy> orderBys = new ArrayList<>();
        config.sort.forEach(orderFieldName -> {
            OrderBy orderBy = new OrderBy();
            orderBy.setFieldName(orderFieldName);
            orderBys.add(orderBy);
        });


        // Create the ReportRequest object.
        ReportRequest request = new ReportRequest()
                .setViewId(config.viewId)
                .setDateRanges(Arrays.asList(dateRange))
                .setMetrics(metrics)
                .setDimensions(dimensions)
                .setPageSize(config.limit)
                .setOrderBys(orderBys);

        if (config.nextPageToken != null) {
            request.setPageToken(config.nextPageToken);
        }

        ArrayList<ReportRequest> requests = new ArrayList<ReportRequest>();
        requests.add(request);

        // Create the GetReportsRequest object.
        GetReportsRequest getReport = new GetReportsRequest()
                .setReportRequests(requests);

        // Call the batchGet method.
        GetReportsResponse response = service.reports().batchGet(getReport).execute();

        // Return the response.
        return response;
    }

    /**
     * Parses and prints the Analytics Reporting API V4 response.
     *
     * @param response An Analytics Reporting API V4 response.
     */
    private void printResponse(GetReportsResponse response) {

        for (Report report: response.getReports()) {
            ColumnHeader header = report.getColumnHeader();
            List<String> dimensionHeaders = header.getDimensions();
            List<MetricHeaderEntry> metricHeaders = header.getMetricHeader().getMetricHeaderEntries();
            List<ReportRow> rows = report.getData().getRows();

            if (rows == null) {
                System.out.println("No data found for " + config.viewId);
                return;
            }

            for (ReportRow row: rows) {
                List<String> dimensions = row.getDimensions();
                List<DateRangeValues> metrics = row.getMetrics();

                for (int i = 0; i < dimensionHeaders.size() && i < dimensions.size(); i++) {
                    System.out.println(dimensionHeaders.get(i) + ": " + dimensions.get(i));
                }

                for (int j = 0; j < metrics.size(); j++) {
                    System.out.print("Date Range (" + j + "): ");
                    DateRangeValues values = metrics.get(j);
                    for (int k = 0; k < values.getValues().size() && k < metricHeaders.size(); k++) {
                        System.out.println(metricHeaders.get(k).getName() + ": " + values.getValues().get(k));
                    }
                }
            }
        }
    }

    private void writeResponseToCsvFile(GetReportsResponse response) {
        //write analytics reports to csv files
        try {
            int fileSuffix = 0;
            for (Report report: response.getReports()) {
                String filename = config.reportName + "-" + fileSuffix + ".csv";
                //while file exists, increment suffix
                while(new File(filename).exists()) {
                    fileSuffix++;
                    filename = config.reportName + "-" + fileSuffix + ".csv";
                }


                ColumnHeader header = report.getColumnHeader();
                List<String> dimensionHeaders = header.getDimensions();
                List<MetricHeaderEntry> metricHeaders = header.getMetricHeader().getMetricHeaderEntries();
                List<ReportRow> rows = report.getData().getRows();

                if (rows == null) {
                    System.out.println("No data found for " + config.viewId);
                    return;
                }


                //write headers
                List<String> headers = new ArrayList<>();
                headers.addAll(dimensionHeaders);
                headers.addAll(metricHeaders.stream().map(MetricHeaderEntry::getName).toList());

                Path path = Paths.get( filename );
                BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
                CSVPrinter csvWriter = new CSVPrinter(writer, CSVFormat.DEFAULT
                        .withHeader( headers.toArray(new String[0]) ));


                //write data
                for (ReportRow row: rows) {
                    List<String> dimensions = row.getDimensions();
                    List<DateRangeValues> metrics = row.getMetrics();
                    List<String> rowData = new ArrayList<>();

                    for (int i = 0; i < dimensions.size(); i++) {
                        rowData.add(dimensions.get(i));

                    }

                    for (int j = 0; j < metrics.size(); j++) {
                        DateRangeValues values = metrics.get(j);
                        for (int k = 0; k < values.getValues().size(); k++) {
                            rowData.add(values.getValues().get(k));
                        }
                    }
                    csvWriter.printRecord(rowData);
                }
                csvWriter.flush();
                csvWriter.close();
            }
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
