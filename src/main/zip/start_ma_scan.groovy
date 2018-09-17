/**
 * � Copyright IBM Corporation 2015.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

import com.urbancode.air.AirPluginTool
import com.urbancode.air.plugin.AppScanSaaS.SCXRestClient
import com.urbancode.air.plugin.AppScanSaaS.ScanType

final def airHelper = new AirPluginTool(args[0], args[1])
final Properties props = airHelper.getStepProperties()

String scanUser = props["scanUser"]
String scanPassword = props["scanPassword"]
String thirdCredential = props['thirdCredential']
String parentjobid = props["parentScanId"]
String appId = props["applicationId"]
String presenceId = props["presenceId"]
String issueCountString = props['reportIssueCountValidation']
long scanTimeout = props["scanTimeout"] ? Long.parseLong(props["scanTimeout"]) : -1
boolean failOnPause = Boolean.parseBoolean(props['failOnPause'])
boolean validateReport = !issueCountString.isEmpty()
int exitCode = 0

SCXRestClient restClient = new SCXRestClient(props)

File apkFile = new File(props["apkFileLocation"])
if (!apkFile.exists()){
    println "[Error] APK file doesn't exist."
    System.exit 1
}

String scanId = restClient.startMobileScan(
    ScanType.Android,
    apkFile,
    scanUser,
    scanPassword,
    thirdCredential,
    parentjobid,
    appId,
    presenceId)

airHelper.setOutputProperty("ScanId", scanId)
airHelper.storeOutputProperties()

if (validateReport){
    long startTime = System.currentTimeMillis()
    exitCode = restClient.waitForScan(scanId, ScanType.Android, issueCountString, startTime, scanTimeout, failOnPause)
}

if (exitCode) {
    println("[Error] Scan has failed validation.")
}
else {
    println("[OK] Scan has completed successfully.")
}

System.exit(exitCode)