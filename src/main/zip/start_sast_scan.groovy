/**
 * (c) Copyright IBM Corporation 2015.
 * (c) Copyright HCL Technologies Ltd. 2018. All Rights Reserved.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

import com.urbancode.air.AirPluginTool
import com.urbancode.air.plugin.AppScanSaaS.SCXRestClient
import com.urbancode.air.plugin.AppScanSaaS.SastScanHelper
import com.urbancode.air.plugin.AppScanSaaS.ScanType

final def airHelper = new AirPluginTool(args[0], args[1])
final Properties props = airHelper.getStepProperties()

String appId = props["applicationId"]
String parentjobid = props["parentScanId"]
String sastFileLocation = props['sastFileLocation']
String issueCountString = props['reportIssueCountValidation']
long scanTimeout = props["scanTimeout"] ? Long.parseLong(props["scanTimeout"]) : -1
boolean mailNotification = props['mailNotification']
boolean failOnPause = Boolean.parseBoolean(props['failOnPause'])
boolean validateReport = !issueCountString.isEmpty()
int exitCode = 0

SCXRestClient restClient = new SCXRestClient(props)
SastScanHelper scanHelper = new SastScanHelper()

if (!sastFileLocation) {
    println "[Error] IRX file/Scan directory has not been specified."
    System.exit 1
}

File arsaFile = new File(sastFileLocation)
if (!arsaFile.exists()) {
    println "[Error] SAST file $arsaFile doesn't exist."
    System.exit 1
}

boolean isGenerateARSA = !(arsaFile.name.endsWith('.irx') || arsaFile.name.endsWith('.arsa'))

if (isGenerateARSA) {
    String arsaToolDir = props['arsaToolDir']

    if (!arsaToolDir) {
        println "[Error] Static Analyzer Client Tool location has not been specified."
        System.exit 1
    }

    String encryptArsa = props['encryptArsa']
    boolean encrypt = true;

    if (encryptArsa) {
        encrypt = Boolean.parseBoolean(encryptArsa)
    }

    arsaFile = scanHelper.generateARSA(arsaToolDir, arsaFile, props["sastConfigFile"], encrypt)

    if (!arsaFile.exists()) {
        println("[Error] IRX file generation failed.")
        System.exit(1)
    }
}

String scanId = restClient.startStaticScan(arsaFile, parentjobid, appId, mailNotification)

if (isGenerateARSA) {
    arsaFile.delete()
}

airHelper.setOutputProperty("ScanId", scanId)
airHelper.storeOutputProperties()

if (validateReport){
    long startTime = System.currentTimeMillis()
    def scan = restClient.waitForScan(scanId, ScanType.SAST, startTime, scanTimeout, failOnPause)
    def issuesJson = scan.LastSuccessfulExecution
    exitCode = restClient.validateScanIssues(issuesJson, scan.Name, scanId, issueCountString)
}

if (exitCode) {
    println("[Error] Scan has failed validation.")
}
else {
    println("[OK] Scan has completed successfully.")
}

System.exit(exitCode)