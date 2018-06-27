package com.appdynamics.extensions.aws.costexplorer;import java.io.BufferedReader;import java.io.IOException;import java.io.StringReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.File;import java.util.HashMap;import java.util.Map;
import java.util.ArrayList;import java.util.regex.Matcher;import java.util.regex.Pattern;
import java.util.Properties;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;import org.apache.log4j.Logger;import com.singularity.ee.agent.systemagent.api.AManagedMonitor;import com.singularity.ee.agent.systemagent.api.MetricWriter;import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;import com.singularity.ee.agent.systemagent.api.TaskOutput;import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;import com.singularity.ee.util.httpclient.HttpExecutionRequest;import com.singularity.ee.util.httpclient.HttpExecutionResponse;import com.singularity.ee.util.httpclient.HttpOperation;import com.singularity.ee.util.httpclient.IHttpClientWrapper;
import org.yaml.snakeyaml.Yaml;
import com.appdynamics.extensions.aws.costexplorer.CostExplorerConfiguration;

import com.amazonaws.services.costexplorer.AWSCostExplorerClientBuilder;
import com.amazonaws.services.costexplorer.AWSCostExplorer;
import com.amazonaws.services.costexplorer.model.GetCostAndUsageRequest;
import com.amazonaws.services.costexplorer.model.GetCostAndUsageResult;
import com.amazonaws.services.costexplorer.model.DateInterval;
import com.amazonaws.services.costexplorer.model.ResultByTime;
import com.amazonaws.services.costexplorer.model.GroupDefinition;
import com.amazonaws.services.costexplorer.model.Group;public class CostExplorerMonitor extends AManagedMonitor
{
	private CostExplorerConfiguration config;
	private boolean initialized = false;
	/**	 ** The metric prefix indicates the metric's position in the AppDynamics metric hierarchy.	 ** It is prepended to the metric name when the metric is uploaded to the Controller.	 ** These metrics can be found in the AppDynamics metric hierarchy under	 ** Application Infrastructure Performance|{@literal <}Node{@literal >}|Custom  Metrics|WebServer|NGinX|Status	 */

	private static final String metricPrefixDefault = String.format("%s%s%s%s",
            "Custom Metrics", "|", "Amazon CostExplorer", "|");

	private static final Logger LOGGER = Logger.getLogger("com.singularity.extensions.aws.CostExplorerMonitor");
	/**	 ** Main execution method that uploads the metrics to the AppDynamics Controller.	 ** @see com.singularity.ee.agent.systemagent.api.ITask#execute(java.util.Map, com.singularity.ee.agent.systemagent.api.TaskExecutionContext)	 */	public TaskOutput execute(Map<String, String> arg0, TaskExecutionContext arg1)			throws TaskExecutionException	{
		try		{
			if(!initialized) {
				loadConfig(arg0.get("config-file"));
			}
                        /* Gets the values for the metrics and populates the resultsMap. */        		/* Hash map to store metric values obtained from the source. */			Map<String,String> resultMap = new HashMap<String,String>();			populate(resultMap);                        /* Outputs the metrics: metric name, value and processing qualifiers. */
			for(String key : resultMap.keySet()) {
				printMetric(key, resultMap.get(key),						MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,						MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE,						MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);
			}
			/* Upload the metrics to the Controller. */			return new TaskOutput("AWS CostExplorer Metrics Upload Complete");		}		catch (Exception e)		{			return new TaskOutput("Error: " + e);		}	}

	private void loadConfig(String pathToConfig) {
		FileInputStream fis = getConfigFile(pathToConfig);
		Yaml yaml = new Yaml();
            	config = yaml.loadAs(fis, CostExplorerConfiguration.class);
		String awsAccessKeyId = config.getAccounts().get(0).getAwsAccessKey();
		String awsSecretKey = config.getAccounts().get(0).getAwsSecretKey();
		LOGGER.debug("****AwsAccessKey: " + awsAccessKeyId);
		LOGGER.debug("****AwsSecretKey: " + awsSecretKey);
		//Set credentials in System Properties
		Properties p = new Properties(System.getProperties());
		p.setProperty("aws.accessKeyId", awsAccessKeyId);
		p.setProperty("aws.secretKey", awsSecretKey);
		System.setProperties(p);
	}

	private FileInputStream getConfigFile(String pathToConfig) {
		FileInputStream fis = null;
		try{
			String workingDir = System.getProperty("user.dir");
			LOGGER.debug("workingDir: " + workingDir);
			String baseDir = workingDir.substring(0, workingDir.lastIndexOf(File.separator, workingDir.length()-2));
			LOGGER.debug("Config file is " + baseDir + File.separator + pathToConfig);
			fis = new FileInputStream(new File(baseDir + File.separator + pathToConfig));
		} catch (FileNotFoundException fnfe2) {
			LOGGER.info("Error loading config file: " + pathToConfig);
		}
		return fis;
	}	/**	 * Fetches Statistics from AWS Cost Explorer Server	 * @throws InstantiationException	 * @throws IllegalAccessException	 * @throws ClassNotFoundException	 * @throws IOException	 */	protected void populate(Map<String,String> resultMap) throws InstantiationException,			IllegalAccessException, ClassNotFoundException, IOException	{
		LOGGER.debug("****InPopulateMap");
		AWSCostExplorer ace = AWSCostExplorerClientBuilder.standard().withRegion("us-east-1").build();
		LOGGER.debug("****Have ace");
		GetCostAndUsageRequest request = new GetCostAndUsageRequest();
		

		
		request.setTimePeriod(getCurrentMonthDateInterval());

		request.setGranularity("MONTHLY");

		ArrayList metric = new ArrayList(1);
		metric.add("UnblendedCost");
		request.setMetrics(metric);

		ArrayList gd_list = new ArrayList(2);
		GroupDefinition gd = new GroupDefinition();
		String businessUnitName = config.getBusinessUnitName();
		LOGGER.debug("*****Business Unit Name is: " + businessUnitName);
		gd.setKey(businessUnitName);
		gd.setType("TAG");
		gd_list.add(gd);
		gd = new GroupDefinition();
		gd.setType("DIMENSION");
		gd.setKey("SERVICE");
		gd_list.add(gd);
		request.setGroupBy(gd_list);
		LOGGER.debug("****ace request is ready to go");

		boolean done = false;


		while(!done) {
			LOGGER.debug("****getCostAndUsage");
			GetCostAndUsageResult response = null;
			try {
    				response = ace.getCostAndUsage(request);
			} catch(Exception e) {
				LOGGER.info(e);
			}
			LOGGER.debug("****Response received");
			if(response != null) {
    				for(ResultByTime resultByTime : response.getResultsByTime()) {
					LOGGER.debug("****ResultByTime received");
					for(Group group : resultByTime.getGroups()) {
						LOGGER.debug("Group received");
						StringBuilder finalTag = new StringBuilder();
	     					String fullTag = group.getKeys().get(0);
             					String tagValue = fullTag.substring(fullTag.indexOf("$") + 1);
	     					if(tagValue == null || tagValue.length() == 0) {
							tagValue = "Empty";
	     					}

             					String serviceName = group.getKeys().get(1);

						finalTag.append(tagValue).append("|").append(serviceName);

	     					String originalAmountStr = group.getMetrics().get("UnblendedCost").getAmount();
						Double amountInCents = Double.parseDouble(originalAmountStr) * 100.00;
						Long amount = Math.round(amountInCents);

						resultMap.put(finalTag.toString(),amount.toString());
        				}
				}
			} else {
				LOGGER.debug("****Response was null");
			}

    			request.setNextPageToken(response.getNextPageToken());

   			if(response.getNextPageToken() == null) {
       				done = true;
   			}
		}
		gd_list.clear();
			}	/**	 * Returns the metric to the AppDynamics Controller.	 * @param 	metricName		Name of the Metric	 * @param 	metricValue		Value of the Metric	 * @param 	aggregation		Average OR Observation OR Sum	 * @param 	timeRollup		Average OR Current OR Sum	 * @param 	cluster			Collective OR Individual	 *	 * Overrides the Metric Writer class printMetric() by building the string that provides all the fields needed to	 * process the metric.	 */	public void printMetric(String metricName, Object metricValue, String aggregation, String timeRollup, String cluster)	{
		LOGGER.info("Sending " + getMetricPrefix() + metricName + " with value " + metricValue + " to Controller.");
		MetricWriter metricWriter = getMetricWriter(getMetricPrefix() + metricName,			aggregation,			timeRollup,			cluster		);		metricWriter.printMetric(String.valueOf(metricValue));	}	protected String getMetricPrefix()	{
		String metricPrefix = new String(config.getMetricPrefix());		if(metricPrefix == null || metricPrefix.length() == 0) {
			return metricPrefixDefault;
		} else {
			return metricPrefix;
		}	}

	private DateInterval getCurrentMonthDateInterval() {
		LocalDate today = LocalDate.now();
		LocalDate firstDayOfMonth = today.withDayOfMonth(1);
		LocalDate lastDayOfMonth = today.withDayOfMonth(today.lengthOfMonth());

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		String startDate = firstDayOfMonth.format(formatter);
		String endDate = lastDayOfMonth.format(formatter);


		DateInterval di = new DateInterval();
		di.withStart(startDate);
		di.withEnd(endDate);
		return di;
	}}