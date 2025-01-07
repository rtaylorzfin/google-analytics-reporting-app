package org.zfin.analytics;

import com.google.analytics.data.v1beta.*;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.services.analyticsreporting.v4.AnalyticsReportingScopes;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class AnalyticsReportRunner {
    private Config config;
    private String credentials;

    public AnalyticsReportRunner(Config config, String credentials) {
        this.config = config;
        this.credentials = credentials;
    }

    /**
     * Queries the Analytics Reporting API V4.
     */
    public List<Path> runReportAndWriteCsvFiles(Path outputDirectory) {
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
            runReportRequestBuilder.addOrderBys(
                    OrderBy.newBuilder().setDimension(
                                    OrderBy.DimensionOrderBy.newBuilder().setDimensionName(orderFieldName)
                            )
//  TODO: add handling of descending sort order
//                    .setDesc(true)
                            .build());
        });

        runReportRequestBuilder.setLimit(config.limit);

        RunReportRequest request = runReportRequestBuilder.build();

        GoogleCredentials gc = null;
        BetaAnalyticsDataSettings betaAnalyticsDataSettings = null;

        try {
            InputStream inputStream = new ByteArrayInputStream(credentials.getBytes(StandardCharsets.UTF_8));
            gc = ServiceAccountCredentials.fromStream(inputStream).createScoped(AnalyticsReportingScopes.all());
            betaAnalyticsDataSettings = BetaAnalyticsDataSettings.newBuilder().setCredentialsProvider(FixedCredentialsProvider.create(gc)).build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<Path> outputFiles = new ArrayList<>();

        try(BetaAnalyticsDataClient service = BetaAnalyticsDataClient.create(betaAnalyticsDataSettings)) {
            RunReportResponse response = service.runReport(request);

            int totalRows = response.getRowCount();
            int rowsInResponse = response.getRowsCount();

//            System.out.println("Total rows: " + totalRows);
//            System.out.println("Rows in response: " + rowsInResponse);

            int pageCount = 1;
//            System.out.println("Getting page " + pageCount + " for " + config.reportName);
            outputFiles.add(writeResponseToCsvFile(response.getRowsList(), outputDirectory));
            while (rowsInResponse < totalRows) {
                request = runReportRequestBuilder.setOffset(rowsInResponse).build();
                response = service.runReport(request);
                rowsInResponse += response.getRowsCount();
                pageCount++;
//                System.out.println("Getting page " + pageCount + " for " + config.reportName);
                outputFiles.add(writeResponseToCsvFile(response.getRowsList(), outputDirectory));
            }
            return outputFiles;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    private Path writeResponseToCsvFile(List<Row> response, Path outputDirectory) {
        //write analytics reports to csv files
        try {
            int fileSuffix = 0;

            File fileFromPath = outputDirectory.toFile();
            String directoryName = fileFromPath.getAbsolutePath();

            String filename = directoryName + "/" + config.reportName + "-" + fileSuffix + ".csv";
            //while file exists, increment suffix
            while(new File(filename).exists()) {
                fileSuffix++;
                filename = directoryName + "/" + config.reportName + "-" + fileSuffix + ".csv";
            }

            //write headers
            List<String> headers = new ArrayList<>();
            headers.addAll(config.metrics);
            headers.addAll(config.dimensions);

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

            return path;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
