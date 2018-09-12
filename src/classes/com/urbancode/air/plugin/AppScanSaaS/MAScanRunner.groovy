/**
 * � Copyright IBM Corporation 2015.
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
        String appUsername = props["appUsername"]
        String appPassword = props["appPassword"]
        String thirdCredential = props['thirdCredential']
        String parentjobid = props["parentScanId"]
        String appId = props["applicationId"]
        String issueCountString = props['reportIssueCountValidation']
        boolean validateReport = !issueCountString.isEmpty()

		File apkFile = new File(props["apkFileLocation"])
		if (!apkFile.exists()){
			println "APK file doesn't exist"
			System.exit 1
		}

        String scanId = restClient.startMobileScan(
            ScanType.Android,
            apkFile,
            appUsername,
            appPassword,
            thirdCredential,
            parentjobid,
            appId)


		Long startTime = System.currentTimeMillis()
		if (validateReport){
			final def scanTimeout = 45
			try {
				scanTimeout = Integer.parseInt(props['scanTimeout'])

			} catch (NumberFormatException){}

			restClient.waitForScan(scanId, ScanType.Android, TimeUnit.MINUTES.toMillis(scanTimeout), startTime, issueCountString, props)
		}

		return scanId;
	}
}