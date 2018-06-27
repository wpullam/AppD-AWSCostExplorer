package com.appdynamics.extensions.aws.costexplorer;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.File;
import java.util.ArrayList;
import java.util.Properties;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.yaml.snakeyaml.Yaml;
import com.appdynamics.extensions.aws.costexplorer.CostExplorerConfiguration;

import com.amazonaws.services.costexplorer.AWSCostExplorerClientBuilder;
import com.amazonaws.services.costexplorer.AWSCostExplorer;
import com.amazonaws.services.costexplorer.model.GetCostAndUsageRequest;
import com.amazonaws.services.costexplorer.model.GetCostAndUsageResult;
import com.amazonaws.services.costexplorer.model.DateInterval;
import com.amazonaws.services.costexplorer.model.ResultByTime;
import com.amazonaws.services.costexplorer.model.GroupDefinition;
import com.amazonaws.services.costexplorer.model.Group;
{
	private CostExplorerConfiguration config;
	private boolean initialized = false;


	private static final String metricPrefixDefault = String.format("%s%s%s%s",
            "Custom Metrics", "|", "Amazon CostExplorer", "|");



		try
			if(!initialized) {
				loadConfig(arg0.get("config-file"));
			}

			for(String key : resultMap.keySet()) {
				printMetric(key, resultMap.get(key),
			}


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
	}
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
		
		LOGGER.info("Sending " + getMetricPrefix() + metricName + " with value " + metricValue + " to Controller.");
		MetricWriter metricWriter = getMetricWriter(getMetricPrefix() + metricName,
		String metricPrefix = new String(config.getMetricPrefix());
			return metricPrefixDefault;
		} else {
			return metricPrefix;
		}

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
	}