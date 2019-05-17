/**
 * (c) Copyright IBM Corporation 2015.
 * (c) Copyright HCL Technologies Ltd. 2018. All Rights Reserved.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

package com.urbancode.air.plugin.AppScanSaaS

import java.io.File
import java.io.InputStream
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

import org.apache.http.HttpResponse
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.InputStreamBody

/**
 * Class that provides functions to make HTTP calls to the ASoC REST API.
 */
public abstract class RestClient {
    protected RestClientHelper restHelper
	protected boolean validateSSL
	protected String token
	protected String authHeader
	protected String baseUrl
	protected String apiEnvironment
    protected String clientType

	protected static final String ANALYZERS_API_BM_DOMAIN = "appscan.bluemix.net"
	protected static final String ASM_API_GATEWAY_DOMAIN = "cloud.appscan.com"
	protected static final String MOBILE_API_PATH_V1 = "/api/%s/MobileAnalyzer/"
	protected static final String SAST_API_PATH_V1 = "/api/%s/StaticAnalyzer/"
	protected static final String DAST_API_PATH_V1 = "/api/%s/DynamicAnalyzer/"
	protected static final String IOS_API_PATH_V1 = "/api/%s/iOS/"

	protected static final String API_METHOD_DOWNLOAD_TOOL_V1 = "DownloadTool"

    private static final String API_V2 = "/api/v2/%s"
    private static final String API_BLUEMIX_LOGIN = "/api/V2/Account/BluemixLogin"
    private static final String API_APIKEY_LOGIN = "/api/V2/Account/ApiKeyLogin"
    private static final String REPORT_TYPE_XML = "Xml"
    private static final String REPORT_TYPE_PDF = "Pdf"
    private static final String REPORT_TYPE_HTML = "Html"
    private static final String REPORT_TYPE_COMPLIANCE_PDF = "CompliancePdf"
    private static final String API_DOWNLOAD_REPORT = "/api/v2/Scans/%s/Report/%s"
    private static final String API_FILE_UPLOAD = "/api/v2/FileUpload"
    private static final String MOBILE_API_PATH = "/api/v2/%s/MobileAnalyzer"
    private static final String SAST_API_PATH = "/api/v2/%s/StaticAnalyzer"
    private static final String DAST_API_PATH = "/api/v2/%s/DynamicAnalyzer"
    protected static final String DAST_FILE_API_PATH = "/api/v2/%s/DynamicAnalyzerWithFile"
    private static final String API_METHOD_SCANS = "Scans"
    private static final String API_RE_SCAN = "/api/v2/Scans/%s/Executions"
    private static final String API_PRESENCES = "/api/v2/Presences"

    private static final String API_DOMAIN_OWNERSHIP = "/api/v2/DomainOwnership"
    private static final String Verify = "/Verify"
    private static final String Register = "/Register/"
    private static final String Confirm = "/Confirm/"

    private static final String NEW_KEY = "NewKey"

    private static final String Linux_x86_64 = "/Linux_x86_64"
    private static final String Win_x86_64 = "/Win_x86_64"

    private static final int MinReportSize = 1024

	public RestClient(Properties props) {
		this.validateSSL = validateSSL
		String userServer = props.containsKey("userServer") ? props["userServer"] : ASM_API_GATEWAY_DOMAIN
		String userServerPort = props.containsKey("userServerPort") ? props["userServerPort"] : "443"

		this.baseUrl = "https://" + userServer + ":" +  userServerPort
        this.restHelper =  new RestClientHelper(baseUrl, false)

        String os = System.getProperty('os.name')
        clientType = "urbancode"

        /* Configure ClientType to submit scan origin to ASoC team */
        if ((os =~ /(?i)windows/).find()) {
            clientType += "-windows"
        }
        else if ((os =~ /(?i)mac/).find()) {
            clientType += "-mac"
        }
        else {
            clientType += "-linux"
        }
	}

	public getBaseUrl() {
		return this.baseUrl
	}

	public File getIPAXGenerator(String dirName) {
		String apiPath = getApiPath_V1(ScanType.IOS);
		String url = "${apiPath}${API_METHOD_DOWNLOAD_TOOL_V1}"
		println "[Action] Sending POST request to ${this.baseUrl}$url."

        restHelper.addRequestHeader("Accept", "application/zip")

        def response = restHelper.doPostRequest(url)
        InputStream zip = response.getEntity().getContent()

        println "[OK] IPAX generator tool successfully received. Deleting ${dirName} before extracting it."
        (new File(dirName)).deleteDir()
        unzip(zip, "", dirName)

		return new File(dirName, "IPAX_Generator.sh")
	}

	public def getScan(String scanId, ScanType scanType) {
		String url = getScanUrl(scanId, scanType)
		def scan = null

        println "[Action] Sending GET request to ${this.baseUrl}$url."

        HttpResponse response = restHelper.doGetRequest(url)

        scan = restHelper.parseResponse(response)

		println "[OK] Scan retrieved successfully. Scan name is: ${scan.Name}"
		return scan
	}

	public def getAllScans(ScanType scanType) {
		String url = getAllScansUrl(scanType)
		def scans = null

        println "[Action] Sending GET request to ${this.baseUrl}$url."

        HttpResponse response = restHelper.doGetRequest(url)
        scans = restHelper.parseResponse(response)

		println "[OK] User scans retrieved successfully"
		return scans
	}

	public void deleteScan(String scanId, ScanType scanType) {
		String url = getDeleteScanUrl(scanId, scanType)

		println "[Action] Sending DELETE request to ${this.baseUrl}$url."

        restHelper.doDeleteRequest(url)
		println "[OK] Scan Id: ${scanId} deleted successfully"
	}

	protected String getApiPath_V1(ScanType scanType) {
		String apiPathFormat = null;
		switch (scanType) {
			case ScanType.Android:
				apiPathFormat = MOBILE_API_PATH_V1
				break
			case ScanType.SAST:
				apiPathFormat = SAST_API_PATH_V1
				break
			case ScanType.IOS:
				apiPathFormat = IOS_API_PATH_V1
				break
			default:
				apiPathFormat = DAST_API_PATH_V1
		}
		return String.format(apiPathFormat, apiEnvironment);
	}

	protected int validateScanIssues(def issuesJson, String scanName, String scanId, String issueCountString) {
		if (issueCountString == null || issueCountString.isEmpty()) {
			return 0
		}

		String[] issueCount = issueCountString.split(",");

		int maxHighSeverityIssues = issueCount.length > 0 ? Integer.parseInt(issueCount[0]) : Integer.MAX_VALUE;
		int maxMediumSeverityIssues = issueCount.length > 1 ? Integer.parseInt(issueCount[1]) : Integer.MAX_VALUE;
		int maxLowSeverityIssues = issueCount.length > 2 ? Integer.parseInt(issueCount[2]) : Integer.MAX_VALUE;
		int maxInformationalSeverityIssues = issueCount.length > 3 ? Integer.parseInt(issueCount[3]) : Integer.MAX_VALUE;
        int exitCode = 0

		println "[OK] Scan ${scanName} with id ${scanId} has ${issuesJson.NHighIssues} high severity issues."
		if (issuesJson.NHighIssues > maxHighSeverityIssues) {
             println("[Error] Scan exceeded high issue count threshold. Result: ${issuesJson.NHighIssues}. "
                 + "Max expected: ${maxHighSeverityIssues}")
             exitCode = 1
		}

		println "[OK] Scan ${scanName} with id ${scanId} has ${issuesJson.NMediumIssues} medium severity issues."
		if (issuesJson.NMediumIssues > maxMediumSeverityIssues) {
            println("[Error] Scan exceeded medium issue count threshold. Result: ${issuesJson.NMediumIssues}. "
                + "Max expected: ${maxMediumSeverityIssues}")
            exitCode = 1
		}

		println "[OK] Scan ${scanName} with id ${scanId} has ${issuesJson.NLowIssues} low severity issues"

        if (issuesJson.NLowIssues > maxLowSeverityIssues) {
            println("[Error] Scan exceeded low issue count threshold. Result: ${issuesJson.NLowIssues}. "
                + "Max expected: ${maxLowSeverityIssues}")
            exitCode = 1
        }

		println "[OK] Scan ${scanName} with id ${scanId} has ${issuesJson.NInfoIssues} informational severity issues"

        if (issuesJson.NInfoIssues > maxInformationalSeverityIssues) {
            println("[Error] Scan exceeded informational issue count threshold. Result: ${issuesJson.NInfoIssues}. "
                + "Max expected: ${maxInformationalSeverityIssues}")
            exitCode = 1
        }

        return exitCode
	}

	protected void unzip(InputStream inputStream, String filePattern, String targetDir) {
		byte[] buffer = new byte[1024]

		try{
			File folder = new File(targetDir)

			folder.deleteDir()
			folder.mkdir()

			ZipInputStream zis = new ZipInputStream(inputStream)
			ZipEntry ze = zis.getNextEntry()

			while(ze != null){
				String fileName = ze.getName()
				if (fileName =~ filePattern) {
					File newFile = new File(targetDir + File.separator + fileName)
					if (ze.isDirectory()) {
						newFile.mkdirs();
						ze = zis.getNextEntry()
						continue
					}
					println "[Action] Unzipping file: ${newFile.getAbsoluteFile()}."
					new File(newFile.getParent()).mkdirs()

					FileOutputStream fos = new FileOutputStream(newFile)

					int len
					while ((len = zis.read(buffer)) > 0) {
						fos.write(buffer, 0, len)
					}
					fos.close()
				}
				ze = zis.getNextEntry()
			}
			zis.closeEntry()
			zis.close()

			println "[OK] Done extracting zip."
		} catch(IOException ex){
			assert false, "Failed extracting zip filr to directory: ${targetDir}. Exception: ${ex.getMessage()}"
		}
	}

	protected void assertLogin() {
		assert token != null, "Login failed. Token is null"
		authHeader = "Bearer " + token
        restHelper.addRequestHeader("Authorization", authHeader)
	}

	protected void scxLogin(String keyId, String keySecret) {
		token = null
		authHeader = null
		apiEnvironment = "SCX";

		Properties props = new Properties()
        props.put("KeyId", keyId)
        props.put("KeySecret", keySecret)

		String url = getScxLoginUrl()
		println("[Action] Sending POST request to ${baseUrl}$url: $props.")

        HttpResponse response = restHelper.doPostRequest(url, props)
        def jsonObj = restHelper.parseResponse(response)
        token = jsonObj.Token
	}

    private String uploadFile(File file) {
        MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create()

        FileInputStream fileStream = new FileInputStream(file)
        entityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
        entityBuilder.addPart("fileToUpload", new InputStreamBody(fileStream, "application/zip", file.getName()))

        String fileId = null
        String url = API_FILE_UPLOAD
        println("[Action] Sending POST request to ${this.baseUrl}$url.")

        restHelper.removeRequestHeader("Content-Type")  // browser must determine correct multipart entity content type
        def response = restHelper.doPostRequest(url, entityBuilder.build())
        restHelper.addRequestHeader("Content-Type", "application/json")

        def json = restHelper.parseResponse(response)
        fileId = json.FileId

        if (fileId == null || fileId.length() < 10) {
            println("[Error] The file was NOT uploaded successfully.")
            System.exit(1)
        }

        return fileId
    }

    /* Scan using an ARSA file for static scans, or an IPAX/APK file for IOS/Android scans */
    private String fileBasedScan(
        ScanType scanType,
        String url,
        String fileId,
        String parentjobid,
        Properties props)
    {
        String scanId = null
        String lastExecutionId = null
        String TechName = scanType.toString()

        /* Check if this is a re-scan */
        if (parentjobid != null && !parentjobid.isEmpty()) {
            props = new Properties()    // Overwrite properties and only specify the scan file
            props.put("FileId", fileId)
            url = String.format(API_RE_SCAN, parentjobid);
            scanId = parentjobid
        }

        props.put("ClientType", clientType) // Configure clientType for both new scans and re-scans
        println "[Action] Sending POST request to ${this.baseUrl}$url , with the following parameters: $props."
        restHelper.addRequestHeader("Content-Type", "application/json")
        def response = restHelper.doPostRequest(url, props)

        println "[OK] Response status line: ${response.getStatusLine()}."
        def json = restHelper.parseResponse(response)

        if (scanId == null) {
            scanId = json.Id
            assert  scanId != null && scanId.length() >= 10, "$TechName scan was NOT started successfully"
            lastExecutionId = json.LatestExecution.Id
        }
        else {
            lastExecutionId = json.Id
        }

        if (lastExecutionId == null || lastExecutionId.length() < 10)
            println("[Error] $TechName lastExecutionID was NOT started successfully.")

        println "[OK] $TechName scan was started successfully. scanId=$scanId , executionId=$lastExecutionId."
        return scanId
    }

    /* Verify presence exists and is active */
    protected void verifyPresenceId(String presenceId) {
        if (presenceId == null || presenceId.trim().isEmpty()) {
            return
        }

        println ("[Action] Waiting for presence to be active. Presence id: ${presenceId}.")

        Long waitTimeout = TimeUnit.MINUTES.toMillis(5)
        Long startTime = System.currentTimeMillis()

        while (true) {
            Thread.sleep(TimeUnit.SECONDS.toMillis(30))

            def presencesData = getPresences()

            println("[OK] Current presences: ${presencesData}.")

            //verify that expected presence exists and online
            boolean idExists = presencesData.any( { presence -> return (presence.Id == presenceId && presence.Status == "Active") })

            if (idExists) {
                println "[OK] Found active presence with id: ${presenceId}."
                break
            }

            assert !(System.currentTimeMillis() - startTime > waitTimeout), "Cannot find active presence with id: " + presenceId
        }
    }

    protected def getPresences() {
        String url = API_PRESENCES

        println("[Action] Sending GET request to ${this.baseUrl}$url.")

        def presencesData = null

        HttpResponse response = restHelper.doGetRequest(url)
        presencesData = restHelper.parseResponse(response)

        return presencesData
    }

    public String startDastScan(
        String startingUrl,
        String loginUsername,
        String loginPassword,
        String thirdCredential,
        String parentjobid,
        String presenceId,
        String testPolicy,
        String appId,
        String scanType,
        String scanFilePath,
        boolean mailNotification)
    {
        Properties props = new Properties()
        String url = null
        String scanId = null
        String lastExecutionId = null
        String testPolicyForPostRequest

        switch (testPolicy) {
            case "Application-Only":
                println("[OK] Test policy 'Application-Only' will be used.")
                testPolicyForPostRequest = "Application-Only.policy"
                break
            case "The Vital Few":
                println("[OK] Test policy 'The Vital Few' will be used.")
                testPolicyForPostRequest = "The Vital Few.policy"
                break
            case "Default":
                println("[OK] Test policy 'Default' will be used.")
                testPolicyForPostRequest = "Default.policy"
                break
            default:
                println("[OK] Default test policy 'Default.policy' will be used.")
                testPolicyForPostRequest = "Default.policy"
        }

        if (parentjobid != null && !parentjobid.isEmpty()) {
            url = String.format(API_RE_SCAN, parentjobid);
            scanId = parentjobid
            props = new Properties()
        }
        else {
            verifyPresenceId(presenceId)

            if ("".equals(scanFilePath) || scanFilePath.isEmpty()) {
                /* Run scan without file */
                url = String.format(DAST_API_PATH, API_METHOD_SCANS)
            }
            else {
                /* Run scan with SCAN or SCANT file */
                url = String.format(DAST_FILE_API_PATH, API_METHOD_SCANS)
                File scanFile = new File(scanFilePath)

                if (scanFile.isFile()) {
                    String fileId = uploadFile(scanFile)
                    props.put("ScanFileId", fileId)
                }
                else {
                    throw new FileNotFoundException("File path ${scanFile.absolutePath} does not exist or is not a file.")
                }
            }

            /* Empty fields are ignored */
            props.putAll([ AppId: appId, ScanName : startingUrl, StartingUrl : startingUrl, LoginUser : loginUsername,
                LoginPassword : loginPassword, ExtraField: thirdCredential, PresenceId : presenceId,
                testPolicy : testPolicyForPostRequest, ScanType: scanType, EnableMailNotification: mailNotification])
        }

        props.put("ClientType", clientType) // Configure clientType for both new scans and re-scans
        println("[Action] Sending POST request to ${this.baseUrl}$url , with the following parameters: $props.")

        HttpResponse response = restHelper.doPostRequest(url, props)

        def json = restHelper.parseResponse(response)

        if (scanId == null) {
            scanId = json.Id
            assert  scanId != null && scanId.length() >= 10, "DAST scan was NOT started successfully"
            lastExecutionId = json.LatestExecution.Id
        }
        else {
            lastExecutionId = json.Id
        }

        if (lastExecutionId == null || lastExecutionId.length() < 10) {
            println("[Error] DAST lastExecutionID was NOT started successfully.")
            System.exit(1)
        }
        println("[OK] DAST scan was started successfully. scanId=$scanId , executionId=$lastExecutionId.")
        return scanId
    }

    public String startMobileScan(
        ScanType scanType,
        File scanFile,
        String appUsername,
        String appPassword,
        String thirdCredential,
        String parentjobid,
        String appId,
        String presenceId,
        boolean mailNotification)
    {
        Properties props = new Properties()
        String fileName = scanFile.getName()
        String fileId = uploadFile(scanFile)    // Upload either the IPAX or APK file
        println("[OK] ${fileName} was uploaded successfully. FileID: ${fileId}.")
        String url = String.format(MOBILE_API_PATH, API_METHOD_SCANS);
        props.putAll([AppId: appId, ApplicationFileId: fileId, ScanName: fileName, LoginUser: appUsername,
            LoginPassword: appPassword, ExtraField: thirdCredential, PresenceId: presenceId,
            EnableMailNotification: mailNotification])

        return fileBasedScan(scanType, url, fileId, parentjobid, props)
    }

    public String startStaticScan(File arsaFile, String parentjobid, String appId, boolean mailNotification) {
        Properties props = new Properties()
        String fileName = arsaFile.getName()
        String fileId = uploadFile(arsaFile)
        println("[OK] ${fileName} was uploaded successfully. FileID: ${fileId}.")
        String url = String.format(SAST_API_PATH, API_METHOD_SCANS)
        props.putAll([AppId: appId, ARSAFileId: fileId, ScanName: fileName,
            EnableMailNotification: mailNotification])

        return fileBasedScan(ScanType.SAST, url, fileId, parentjobid, props)
    }

    public def waitForScan(
        String scanId,
        ScanType scanType,
        long startTime,
        long timeout,
        boolean failOnPause)
    {
        println("[Action] Waiting for scan with id '${scanId}'.")

        def scan = null
        String status = null
        String executionProgress = null

        while (true) {
            Thread.sleep(TimeUnit.MINUTES.toMillis(1))

            scan = getScan(scanId, scanType)
            status = scan.LatestExecution.Status
            executionProgress = scan.LatestExecution.ExecutionProgress
            println("[OK] Scan '${scan.Name}' , with id '${scan.Id}' , status is '$status' , ExecutionProgress is '$executionProgress'.")
            if (!status.equalsIgnoreCase("Running") || (failOnPause && executionProgress.equalsIgnoreCase("Paused"))) {
                break
            }
            if (timeout != -1 && (System.currentTimeMillis() - startTime) > timeout) {
                println("[Error] Scan did not complete within the given timeout value.")
                break
            }
        }

        if (!status.equalsIgnoreCase("Ready")) {
            println("[Error] Scan status is '$status' and not 'Ready'. (ExecutionProgress is '$executionProgress').")
            System.exit(1)
        }

        downloadReport(scanId, scanType)

        return scan
    }

    private void downloadSingleReportType(String scanId, String reportType) {
        String url =  String.format(API_DOWNLOAD_REPORT, scanId, reportType)

        println("[Action] Sending GET request to ${this.baseUrl}$url.")

        def response = restHelper.doGetRequest(url)

        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        response.getEntity().writeTo(baos)
        byte[] bytes = baos.toByteArray()

        println("[OK] Response status line: ${response.statusLine}.")
        if (bytes.length <= RestClient.MinReportSize) {
            println("[Error] Report size '${bytes.length}' is invalid.")
            System.exit(1)
        }

        println("[OK] Report type ${reportType} downloaded successfully.")
    }

    public void downloadReport(String scanId, ScanType scanType) {
        downloadSingleReportType(scanId, REPORT_TYPE_XML)
        if (scanType == ScanType.SAST) {
            downloadSingleReportType(scanId, REPORT_TYPE_HTML)
        }
        else {
            downloadSingleReportType(scanId, REPORT_TYPE_PDF)
        }
    }

    protected String getApiPath(ScanType scanType) {
        String apiPath = null;
        switch (scanType) {
            case ScanType.Android:
                apiPath = MOBILE_API_PATH;
                break
            case ScanType.SAST:
                apiPath = SAST_API_PATH;
                break
            case ScanType.IOS:
                apiPath = MOBILE_API_PATH;
                break
            default:
                apiPath = DAST_API_PATH;
        }
        return apiPath
    }

    protected String getScanUrl(String scanId, ScanType scanType)
    {
        String path = String.format(getApiPath(scanType), API_METHOD_SCANS)
        return "${path}/${scanId}"
    }

    protected def getAllScansUrl(ScanType scanType)
    {
        return String.format(API_V2, API_METHOD_SCANS)
    }

    protected String getDeleteScanUrl(String scanId, ScanType scanType)
    {
        return String.format(API_V2, API_METHOD_SCANS).concat("/${scanId}")
    }

    protected String getBluemixLoginUrl()
    {
        return API_BLUEMIX_LOGIN
    }

    protected String getScxLoginUrl()
    {
        return API_APIKEY_LOGIN
    }

    public String createNewPresence() {
        println("[Action] Creating new presence.")

        Date currentTime = new Date();

        String url = API_PRESENCES

        println("[Action] Sending POST request to ${this.baseUrl}$url.")

        Properties props = new Properties()

        props.put("PresenceName", "Groovy Presence" + currentTime)
        def response = restHelper.doPostRequest(url, props)

        def json = restHelper.parseResponse(response)
        String presenceId = json.Id

        return presenceId
    }

    public void renewPresenceKeyFile(String serviceDirectory, String presenceId) {
        println("[Action] Downloading new key file for presence with id: ${presenceId}.")

        String url = API_PRESENCES + "/" + presenceId + "/" + NEW_KEY

        println("[Action] Sending POST request to ${this.baseUrl}$url.")
        HttpResponse response = restHelper.doPostRequest(url, null)

        println("[OK] Response status line: ${response.statusLine}.")

        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        response.getEntity().writeTo(baos)
        byte[] bytes = baos.toByteArray()

        File keyFile = new File(serviceDirectory, "AppScanPresence.key")
        try {
            boolean deleted = keyFile.delete();
            if (deleted) {
                println("[OK] Deleted presence key file ${keyFile.getAbsolutePath()}.")
                keyFile.getParentFile().mkdirs();
                keyFile.createNewFile();
            }
        } catch (Exception e) {
            println "Failed deleting old presence key file ${keyFile.getAbsolutePath()} with exception: ${e.getMessage()}"
        }

        (keyFile).withOutputStream {
            it.write(bytes)
        }
    }

    public void downloadAppscanPresence(String serviceDirectory, boolean isWindows, String presenceId) {
        println("[Action] Downloading Appscan Presence with ID ${presenceId}.")

        String url = API_PRESENCES + "/${presenceId}/Download" + (isWindows ? Win_x86_64 : Linux_x86_64)

        File serviceDir = new File(serviceDirectory)

        println("[Action] Sending POST request to ${this.baseUrl}$url.")
        restHelper.addRequestHeader("Accept", "application/zip")
        HttpResponse response = restHelper.doPostRequest(url, null)

        InputStream zip = response.getEntity().getContent()
        serviceDir.deleteDir()
        unzip(zip, "", serviceDir.getName())
    }

    public void deletePresence(String presenceId, boolean deleteAll) {
        if (deleteAll) {
            println("[Action] Deleting all existing presences.")

            def presencesData = getPresences()

            println("[OK] Current presences: ${presencesData}.")

            presencesData.each { presence ->
                String url = API_PRESENCES + "/" + presence.Id
                println("[Action] Sending DELETE request to ${baseUrl}$url.")
                println("[Action] Deleting presence with ID: ${presence.Id}.")

                restHelper.doDeleteRequest(url)
            }
        }
        else if (!"".equals(presenceId)){
            String url = API_PRESENCES + "/${presenceId}"
            println("[Action] Sending DELETE request to ${baseUrl}$url.")
            println("[Action] Deleting presence with ID: ${presenceId}.")

            restHelper.doDeleteRequest(url)
        }
        else {
            println("[Warning] You have neither specified a presence ID nor checked the "
                + "'Delete All Presences' checkbox. No action will be taken.")
        }
    }

    public static enum DORegistrationType
    {
        Email,
        Html
    }

    public static enum AdminMailPrefix
    {
        Admin,
        Administrator,
        HostMaster,
        Root,
        WebMaster,
        PostMaster
    }

    public boolean isDomainVerified(String urlToVerify) {
        println("[Action] Verifying domain for url: ${urlToVerify}.")

        String url = API_DOMAIN_OWNERSHIP + Verify

        Properties props = new Properties()
        props.put("STP", urlToVerify)
        println("[Action] Sending POST request to ${this.baseUrl}$url: $props.")
        HttpResponse response = restHelper.doPostRequest(url, props)

        println("[OK] Response status line: ${response.statusLine}.")

        String responseText = response.entity.content.text
        println("[OK] Response for url verification is: ${responseText}.")

        boolean result = Boolean.parseBoolean(responseText)

        return result
    }

    protected abstract def login()
}
