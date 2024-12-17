package org.zfin.analytics;

import java.util.List;

/**
 * Use this class to serialize from a json config file like:
  {
    applicationName: "Hello Analytics Reporting",
    viewId: "4490530",
    metrics: [ "ga:users" ],
    dimensions: [ "ga:country" ],
    startDate: "7daysAgo",
    endDate: "today",
    limit: 10000,
    sort: [ "ga:date" ]
  }
 */
public class Config {
    public String applicationName;
    public String reportName;
    public List<String> metrics;
    public List<String> dimensions;
    public String startDate;
    public String endDate;
    public Integer limit;
    public List<String> sort;
    public String propertyId;
}