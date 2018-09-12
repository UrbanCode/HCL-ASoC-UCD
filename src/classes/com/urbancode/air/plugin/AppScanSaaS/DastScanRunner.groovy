/**
 * © Copyright IBM Corporation 2015.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

package com.urbancode.air.plugin.AppScanSaaS
import java.util.concurrent.TimeUnit

import com.urbancode.air.plugin.AppScanSaaS.RestClient
import com.urbancode.air.plugin.AppScanSaaS.ScanType


public class DastScanRunner {
	public static String runDastScan(Properties props, RestClient restClient) {
        String appId = props["applicationId"]
        String startingUrl = props["startingUrl"]
		String issueCountString = props['reportIssueCountValidation'] // Fail count threshold
        String scanUser = props["scanUser"]
        String scanPassword = props["scanPassword"]
        String thirdCredential = props['thirdCredential']
        String parentjobid = props["parentScanId"]
        String scanType = props["scanType"]
        String scanFilePath = props["scanFile"]
        String presenceId = props["presenceId"]
        String testPolicy = props["testPolicy"]
        boolean validateReport = !issueCountString.isEmpty()   // Do not validate report if no fail condition

		if (startingUrl == null || startingUrl.isEmpty()){
			println "Missing starting url"
			System.exit 1
		}

		String scanId = restClient.startDastScan(
            startingUrl,
            scanUser,
            scanPassword,
            thirdCredential,
            parentjobid,
            presenceId,
            testPolicy,
            appId,
            scanType,
            scanFilePath)

		Long startTime = System.currentTimeMillis()
		if (validateReport){
			final def scanTimeout = 45
			try {
				scanTimeout = Integer.parseInt(props['scanTimeout'])
			} catch (NumberFormatException){
			}

			restClient.waitForScan(scanId, ScanType.DAST, TimeUnit.MINUTES.toMillis(scanTimeout), startTime, issueCountString, props)
		}

		return scanId
	}
}