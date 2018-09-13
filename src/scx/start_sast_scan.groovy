/**
 * � Copyright IBM Corporation 2015.
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
boolean validateReport = !issueCountString.isEmpty()

SCXRestClient restClient = new SCXRestClient(props)
SastScanHelper scanHelper = new SastScanHelper()

if (!sastFileLocation) {
    println "sastFileLocation has not been specified."
    System.exit 1
}

File arsaFile = new File(sastFileLocation)
if (!arsaFile.exists()) {
    println "SAST file $arsaFile doesn't exist."
    System.exit 1
}

boolean isGenerateARSA = !(arsaFile.name.endsWith('.irx') || arsaFile.name.endsWith('.arsa'))

if (isGenerateARSA) {
    String arsaToolDir = props['arsaToolDir']

    if (!arsaToolDir) {
        println "arsaToolDir has not been specified."
        System.exit 1
    }

    String encryptArsa = props['encryptArsa']
    boolean encrypt = true;

    if (encryptArsa) {
        encrypt = Boolean.parseBoolean(encryptArsa)
    }

    arsaFile = scanHelper.generateARSA(arsaToolDir, arsaFile, props["sastConfigFile"], encrypt)
    assert arsaFile.exists(), 'IRX file generation failed.'
}

String scanId = restClient.startStaticScan(arsaFile, parentjobid, appId)

if (isGenerateARSA) {
    arsaFile.delete()
}

airHelper.setOutputProperty("ScanId", scanId)
airHelper.storeOutputProperties()

if (validateReport){
    Long startTime = System.currentTimeMillis()
    int exitCode = restClient.waitForScan(scanId, ScanType.SAST, startTime, issueCountString, props)
    System.exit(exitCode)
}