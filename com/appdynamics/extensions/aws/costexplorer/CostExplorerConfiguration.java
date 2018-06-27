/*
 *   Copyright 2018. AppDynamics LLC and its affiliates.
 *   All Rights Reserved.
 *   This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 *   The copyright notice above does not evidence any actual or intended publication of such source code.
 *
 */

package com.appdynamics.extensions.aws.costexplorer;

import com.appdynamics.extensions.aws.costexplorer.Account;
import java.util.List;

/**
 * @author Wayne Pullam
 */
public class CostExplorerConfiguration {


	private List<Account> accounts;
	private String businessUnitName;
	private String metricPrefix;

    public List<Account> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<Account> accounts) {
        this.accounts = accounts;
    }

    public String getBusinessUnitName() {
	return businessUnitName;
    }

    public void setBusinessUnitName(String businessUnitName) {
	this.businessUnitName = businessUnitName;
    }

    public String getMetricPrefix() {
	return metricPrefix;
    }

    public void setMetricPrefix(String metricPrefix) {
	this.metricPrefix = metricPrefix;
    }
}
