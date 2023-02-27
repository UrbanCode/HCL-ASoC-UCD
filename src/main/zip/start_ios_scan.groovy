/**
 * (c) Copyright IBM Corporation 2015.
 * (c) Copyright HCL Technologies Ltd. 2018. All Rights Reserved.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

import com.urbancode.air.AirPluginTool
import com.urbancode.air.plugin.AppScanSaaS.SCXRestClient
import com.urbancode.air.plugin.AppScanSaaS.IOSScanHelper
import com.urbancode.air.plugin.AppScanSaaS.ScanType

import java.io.*
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

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
boolean mailNotification = props['mailNotification']
boolean failOnPause = Boolean.parseBoolean(props['failOnPause'])
boolean validateReport = !issueCountString.isEmpty()
int exitCode = 0

SCXRestClient restClient = new SCXRestClient(props)
IOSScanHelper scanHelper = new IOSScanHelper()
File scanFile = null

String ipaFileLocation = props["ipaFileLocation"]
if (ipaFileLocation!=null && ipaFileLocation.length() > 0) {
    scanFile = new File(ipaFileLocation)
    if (!scanFile.exists()){
        println "[Error] Input ipa file does not exist."
        System.exit 1
    }
    else{
        println "[Action] Starting iOS scan based on the provided ipa file $ipaFileLocation."
    }
}
else {
    /* Currently an unused feature that will be added in a later iteration */
    String projectLocation = props["projectLocation"]
    if (projectLocation==null || projectLocation.length() <= 0) {
        println "[Error] Not enough input was provided for this step. Please add a value to the required "
            + "'IPA file location' field ('ipaFileLocation')."
        System.exit 1
    }
    File projectFile = new File(projectLocation)
    if (!projectFile.exists()){
        println "[Error] Project/Workspace doesn't exist."
        System.exit 1
    }

    scanFile = scanHelper.generateIPAX(restClient, projectFile, props);
}

String scanId = restClient.startMobileScan(
    ScanType.IOS,
    scanFile,
    scanUser,
    scanPassword,
    thirdCredential,
    parentjobid,
    appId,
    presenceId,
    mailNotification)

airHelper.setOutputProperty("ScanId", scanId)
airHelper.storeOutputProperties()

if (validateReport){
    long startTime = System.currentTimeMillis()
    def scan = restClient.waitForScan(scanId, ScanType.IOS, startTime, scanTimeout, failOnPause)
    println("scan");
    println(scan);
    println(scan.getClass());
    def issuesJson = scan.LatestExecution
    println(issuesJson)
    def issuesJson1 = JsonOutput.toJson(issuesJson)
    println(issuesJson1)
    def slurper = new JsonSlurper();
    def issuesJson2 = slurper.parseText(issuesJson1)
    println("result required")
    println(issuesJson2.NHighIssues)

    exitCode = restClient.validateScanIssues(issuesJson2, scan.Name, scanId, issueCountString)
}

if (exitCode) {
    println("[Error] Scan has failed validation.")
}
else {
    println("[OK] Scan has completed successfully.")
}

System.exit(exitCode)