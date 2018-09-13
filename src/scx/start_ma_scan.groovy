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

String appUsername = props["appUsername"]
String appPassword = props["appPassword"]
String thirdCredential = props['thirdCredential']
String parentjobid = props["parentScanId"]
String appId = props["applicationId"]
String issueCountString = props['reportIssueCountValidation']
boolean validateReport = !issueCountString.isEmpty()

SCXRestClient restClient = new SCXRestClient(props)

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

airHelper.setOutputProperty("ScanId", scanId)
airHelper.storeOutputProperties()

if (validateReport){
    Long startTime = System.currentTimeMillis()
    int exitCode = restClient.waitForScan(scanId, ScanType.Android, startTime, issueCountString, props)
    System.exit(exitCode)
}