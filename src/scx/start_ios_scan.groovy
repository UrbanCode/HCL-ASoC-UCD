import com.urbancode.air.plugin.AppScanSaaS.iOSScanHelper

/**
 * � Copyright IBM Corporation 2015.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

import com.urbancode.air.AirPluginTool
import com.urbancode.air.plugin.AppScanSaaS.SCXRestClient
import com.urbancode.air.plugin.AppScanSaaS.IOSScanHelper
import com.urbancode.air.plugin.AppScanSaaS.ScanType


final def airHelper = new AirPluginTool(args[0], args[1])
final Properties props = airHelper.getStepProperties()

String appUsername = props["appUsername"]
String appPassword = props["appPassword"]
String thirdCredential = props['thirdCredential']
String parentjobid = props["parentScanId"]
String appId = props["applicationId"]
String issueCountString = props['reportIssueCountValidation'];
boolean validateReport = !issueCountString.isEmpty();

SCXRestClient restClient = new SCXRestClient(props)
IOSScanHelper scanHelper = new IOSScanHelper()
File scanFile = null

String ipaFileLocation = props["ipaFileLocation"];
if (ipaFileLocation!=null && ipaFileLocation.length() > 0) {
    scanFile = new File(ipaFileLocation)
    if (!scanFile.exists()){
        println "Input ipa file does not exist"
        System.exit 1
    }
    else{
        println "Starting iOS scan based on the provided ipa file $ipaFileLocation"
    }
}
else {
    String projectLocation = props["projectLocation"];
    if (projectLocation==null || projectLocation.length() <= 0) {
        println "Not enough input was provided for this step. Please add a value to the required "
            + "'IPA file location' field ('ipaFileLocation')"
        System.exit 1
    }
    File projectFile = new File(projectLocation)
    if (!projectFile.exists()){
        println "Project/Workspace doesn't exist"
        System.exit 1
    }

    scanFile = scanHelper.generateIPAX(restClient, projectFile, props);
}

String scanId = restClient.startMobileScan(
    ScanType.IOS,
    scanFile,
    appUsername,
    appPassword,
    thirdCredential,
    parentjobid,
    appId)

airHelper.setOutputProperty("ScanId", scanId)
airHelper.storeOutputProperties()

if (validateReport){
    Long startTime = System.currentTimeMillis()
    int exitCode = restClient.waitForScan(scanId, ScanType.IOS, startTime, issueCountString, props)
    System.exit(exitCode)
}