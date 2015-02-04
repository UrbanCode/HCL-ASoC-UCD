/**
 * � Copyright IBM Corporation 2014.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

package com.urbancode.air.plugin.AppScanSaaS
import java.util.concurrent.TimeUnit;
import com.urbancode.air.plugin.AppScanSaaS.Environment;
import com.urbancode.air.plugin.AppScanSaaS.RestClient
import com.urbancode.air.plugin.AppScanSaaS.ScanType

public class DastScanRunner {
	public static String runDastScan(Properties props, RestClient restClient) {
		final def validateReport = false;
		String issueCountString = "";
		if (props.containsKey("validateReport")) {
			validateReport = Boolean.valueOf(props['validateReport'])
		} else if (props.containsKey("reportIssueCountValidation")) {
			issueCountString = props['reportIssueCountValidation'];
			validateReport = !issueCountString.isEmpty();
		}

		String startingUrl = props["startingUrl"]
		if (startingUrl == null || startingUrl.isEmpty()){
			println "Missing starting url"
			System.exit 1
		}

		String scanUser = props["scanUser"]
		String scanPassword = props["scanPassword"]
		String parentjobid = props["parentScanId"]
		
		String scanId = restClient.startDastScan(startingUrl, scanUser, scanPassword, parentjobid)

		Long startTime = System.currentTimeMillis()
		if (validateReport){
			final def scanTimeout = 45
			try {
				scanTimeout = Integer.parseInt(props['scanTimeout'])
			} catch (NumberFormatException){
			}

			restClient.waitForScan(scanId, ScanType.DAST, TimeUnit.MINUTES.toMillis(scanTimeout), startTime, issueCountString)
		}
		
		return scanId;
	}
}