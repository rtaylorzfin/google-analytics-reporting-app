Analytics Reporting App
======================

Use this app to generate reports from Google Analytics.  Some of the reports
can be used as a basic export functionality.  The reports in this app were originally
intended to be used for import into a Plausible Analytics instance.  

Examples of reports that are used by the plausible project:
https://github.com/plausible/analytics/blob/ad12e1ef315c9b4c8eabb20d8cd6a86f3262fc97/lib/plausible/google/report_request.ex#L24

Example usage:
---

First you have to set up your Google Analytics credentials (https://developers.google.com/analytics/devguides/reporting/data/v1/quickstart-client-libraries).  
Make sure you save the credentials file as `credentials.json` in the root of this project.

Once you have your credentials set up, you can run the app with the following command:

```
GOOGLE_APPLICATION_CREDENTIALS=credentials.json mvn compile exec:java -Dexec.args="./reports/pagePath/index.json"
```

The first time you run this app, you may get an error saying that this is the first time using the api and you have to follow
a link from the error to enable it.

Report Configurations:
---
The report config file is a JSON file that contains the configuration for the report you want to run.
Example:

```
{
  "applicationName": "Hello Analytics Reporting",
  "propertyId": "123456",
  "viewId": "N/A",
  "reportName": "visitors",
  "metrics": [
    "users",
  ],
  "dimensions": [
    "date"
  ],
  "startDate": "2020-08-01",
  "endDate": "today",
  "limit": 10000,
  "sort": [
    "date"
  ]
}
```

The property id can be found in the Google Analytics admin panel.  You can also find it in the URL
when you are looking at a report in the Google Analytics UI.

### GA4

This code has been changed to work with GA4.  The earlier version of this code was for Universal Analytics and is tagged as `universal-analytics`.  
There may be more examples to draw from in the `universal-analytics` branch.  They would just need to be converted slightly to work with GA4.