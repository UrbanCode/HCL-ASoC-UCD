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

SCXRestClient restClient = new SCXRestClient(props)

if (startingUrl == null || startingUrl.isEmpty()){
    println "Missing starting url"
    System.exit(1)
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

airHelper.setOutputProperty("ScanId", scanId)
airHelper.storeOutputProperties()

if (validateReport) {
    Long startTime = System.currentTimeMillis()
    int exitCode = restClient.waitForScan(scanId, ScanType.DAST, startTime, issueCountString, props)
    System.exit(exitCode)
}