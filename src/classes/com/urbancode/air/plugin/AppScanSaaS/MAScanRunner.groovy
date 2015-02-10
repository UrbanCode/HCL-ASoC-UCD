/**
 * © Copyright IBM Corporation 2015.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

package com.urbancode.air.plugin.AppScanSaaS

import java.util.concurrent.TimeUnit;
import com.urbancode.air.plugin.AppScanSaaS.RestClient
import com.urbancode.air.plugin.AppScanSaaS.ScanType

public class MAScanRunner {
	public static String runMAScan(Properties props, RestClient restClient) {
		final def validateReport = false;
		String issueCountString = "";
		if (props.containsKey("validateReport")) {
			validateReport = Boolean.valueOf(props['validateReport'])
		} else if (props.containsKey("reportIssueCountValidation")) {
			issueCountString = props['reportIssueCountValidation'];
			validateReport = !issueCountString.isEmpty();
		}
		
		File apkFile = new File(props["apkFileLocation"])
		if (!apkFile.exists()){
			println "APK file doesn't exist"
			System.exit 1
		}
		
		String parentjobid = props["parentScanId"]
		
		String scanId = restClient.uploadAPK(apkFile, parentjobid)
		
		
		Long startTime = System.currentTimeMillis()
		if (validateReport){
			final def scanTimeout = 45
			try {
				scanTimeout = Integer.parseInt(props['scanTimeout'])
				
			} catch (NumberFormatException){}
			
			restClient.waitForScan(scanId, ScanType.Mobile, TimeUnit.MINUTES.toMillis(scanTimeout), startTime, issueCountString)
		}
		
		return scanId;
	}
}