package org.zfin.analytics;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class AnalyticsReportingApp {
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String KEY_FILE_LOCATION = "credentials.json";
    private final String configFilename;
    private Config config;
    private String credentials;
    private Path outputDirectory;
    private AnalyticsReportRunner analyticsReportRunner;

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
            initialize();
            analyticsReportRunner = new AnalyticsReportRunner(config, credentials);
            List<Path> outputFiles = analyticsReportRunner.runReportAndWriteCsvFiles(outputDirectory);
            System.out.println("Output files written to: " + outputDirectory);
            outputFiles.forEach(System.out::println);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initialize() throws Exception {
        loadConfigJson();
        loadCredentials();
        outputDirectory = calculateOutputDirectory();
    }

    private Path calculateOutputDirectory() {
        String directoryName = new File(this.configFilename).getParent();
        if (new File(directoryName + "/csv").exists()) {
            directoryName = directoryName + "/csv";
        }
        return Path.of(directoryName);
    }

    private void loadCredentials() {
        try {
            String credentialsLocation = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            credentials = new String(Files.readAllBytes(Paths.get(credentialsLocation)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
