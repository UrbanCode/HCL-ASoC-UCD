/**
 * � Copyright IBM Corporation 2015.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

package com.urbancode.air.plugin.AppScanSaaS

import groovy.json.JsonSlurper
import groovyx.net.http.*

import java.io.File;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.RedirectStrategy
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.InputStreamBody
import org.apache.http.entity.mime.content.StringBody
import org.apache.http.conn.ssl.SSLContextBuilder
import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.DefaultRedirectStrategy
import org.apache.http.impl.client.HttpClients
import org.apache.http.protocol.HttpContext;

public abstract class RestClient {
    protected RestClientHelper restHelper
	protected boolean validateSSL;
	protected String token
	protected String authHeader

	protected String baseUrl
	protected String apiEnvironment;

	protected static final String ANALYZERS_API_BM_DOMAIN = "appscan.bluemix.net"
	protected static final String ASM_API_GATEWAY_DOMAIN = "appscan.ibmcloud.com"
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

	public RestClient(Properties props, boolean validateSSL, boolean useAsmGatewayAsDefault) {
		this.validateSSL = validateSSL
		String userServer = props.containsKey("userServer") ? props["userServer"] : (useAsmGatewayAsDefault ? ASM_API_GATEWAY_DOMAIN : ANALYZERS_API_BM_DOMAIN)
		String userServerPort = props.containsKey("userServerPort") ? props["userServerPort"] : "443"

		this.baseUrl = "https://" + userServer + ":" +  userServerPort

        this.restHelper =  new RestClientHelper(baseUrl, false)
	}

	public getBaseUrl() {
		return this.baseUrl
	}

	public File getIPAXGenerator(String dirName) {
		String apiPath = getApiPath_V1(ScanType.IOS);
		String url = "${apiPath}${API_METHOD_DOWNLOAD_TOOL_V1}"
		println "Send POST request to ${this.baseUrl}$url"

        restHelper.addRequestHeader("Accept", "application/zip")

        def response = restHelper.doPostRequest(url)
        InputStream zip = response.getEntity().getContent()

        println "IPAX generator tool successfully received. Deleting ${dirName} before extracting it."
        (new File(dirName)).deleteDir()
        unzip(zip, "", dirName)

		return new File(dirName, "IPAX_Generator.sh")
	}

	public def getScan(String scanId, ScanType scanType) {
		String url = getScanUrl(scanId, scanType)
		def scan = null

        println "Send GET request to ${this.baseUrl}$url"

        HttpResponse response = restHelper.doGetRequest(url)

        scan = restHelper.parseResponse(response)

		println "Scan retrieved successfully. Scan name is: ${scan.Name}"
		return scan
	}

	public def getAllScans(ScanType scanType) {
		String url = getAllScansUrl(scanType)
		def scans = null

        println "Send GET request to ${this.baseUrl}$url"

        HttpResponse response = restHelper.doGetRequest(url)
        scans = restHelper.parseResponse(response)

		println "User scans retrieved successfully"
		return scans
	}

	public void deleteScan(String scanId, ScanType scanType) {
		String url = getDeleteScanUrl(scanId, scanType)

		println "Send DELETE request to ${this.baseUrl}$url"

        restHelper.doDeleteRequest(url)
		println "scan Id ${scanId} deleted successfully"
	}

	protected String getApiPath_V1(ScanType scanType) {
		String apiPathFormat = null;
		switch (scanType) {
			case ScanType.Mobile:
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

	protected void validateScanIssues(def issuesJson, String scanName, String scanId, String issueCountString) {
		if (issueCountString == null || issueCountString.isEmpty()) {
			return
		}

		String[] issueCount = issueCountString.split(",");

		int maxHighSeverityIssues = issueCount.length > 0 ? Integer.parseInt(issueCount[0]) : Integer.MAX_VALUE;
		int maxMediumSeverityIssues = issueCount.length > 1 ? Integer.parseInt(issueCount[1]) : Integer.MAX_VALUE;
		int maxLowSeverityIssues = issueCount.length > 2 ? Integer.parseInt(issueCount[2]) : Integer.MAX_VALUE;
		int maxInformationalSeverityIssues = issueCount.length > 3 ? Integer.parseInt(issueCount[3]) : Integer.MAX_VALUE;

		println "Scan ${scanName} with id ${scanId} has ${issuesJson.NHighIssues} high severity issues"
		assert issuesJson.NHighIssues <= maxHighSeverityIssues, "Scan failed to meet high issue count. Result: ${issuesJson.NHighIssues}. Max expected: ${maxHighSeverityIssues}"

		println "Scan ${scanName} with id ${scanId} has ${issuesJson.NMediumIssues} medium severity issues"
		assert issuesJson.NMediumIssues <= maxMediumSeverityIssues, "Scan failed to meet medium issue count. Result: ${issuesJson.NMediumIssues}. Max expected: ${maxMediumSeverityIssues}"

		println "Scan ${scanName} with id ${scanId} has ${issuesJson.NLowIssues} low severity issues"
		assert issuesJson.NLowIssues <= maxLowSeverityIssues, "Scan failed to meet low issue count. Result: ${issuesJson.NLowIssues}. Max expected: ${maxLowSeverityIssues}"

		println "Scan ${scanName} with id ${scanId} has ${issuesJson.NInfoIssues} informational severity issues"
		assert issuesJson.NInfoIssues <= maxInformationalSeverityIssues, "Scan failed to meet informational issue count. Result: ${issuesJson.NInfoIssues}. Max expected: ${maxInformationalSeverityIssues}"
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
					println "Unzipping file: ${newFile.getAbsoluteFile()}"
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

			println "Done extracting zip"
		} catch(IOException ex){
			assert false, "Failed extracting zip filr to directory: ${targetDir}. Exception: ${ex.getMessage()}"
		}
	}

	protected void assertLogin() {
		assert token != null, "Login failed. Token is null"
		authHeader = "Bearer " + token
        restHelper.addRequestHeader("Authorization", authHeader)
	}

	protected void bluemixLogin(String username, String password) {
		token = null
		authHeader = null
		apiEnvironment = "BlueMix";

		Properties props = new Properties()
        props.put("Username", username)
        props.put("Password", password)

		String url = getBluemixLoginUrl()
		println "Send POST request to ${baseUrl}$url: $props"

        try{
            HttpResponse response = restHelper.doPostRequest(url, props)
            def jsonObj = restHelper.parseResponse(response)
            token = jsonObj.Token
        }
        catch (Exception e) {
            println "Failed bluemixLogin with exception: ${e.getMessage()}"
        }
	}

	protected void scxLogin(String keyId, String keySecret) {
		token = null
		authHeader = null
		apiEnvironment = "SCX";

		Properties props = new Properties()
        props.put("KeyId", keyId)
        props.put("KeySecret", keySecret)

		String url = getScxLoginUrl()
		println "Send POST request to ${baseUrl}$url: $props"

		try{
            HttpResponse response = restHelper.doPostRequest(url, props)
            def jsonObj = restHelper.parseResponse(response)
            token = jsonObj.Token
		}
		catch (Exception e) {
			println "Failed scxLogin with exception: ${e.getMessage()}"
		}
	}

	protected def parseJsonFromResponseText(HttpResponseDecorator resp, String errorMessage) {
		def json;
		try{
			String text = resp.entity.content.text
			String contentType = resp.headers."Content-Type"
			assert  contentType?.startsWith("application/json"), "${errorMessage} -> the response headers did not have content-Type application/json"
			json = new JsonSlurper().parseText(text)
		}
		catch (Exception e) {
			println "${errorMessage} -> parseJsonFromResponseText failed, with this exception: ${e.getMessage()}"
		}
		return json
	}

    private String uploadFile(File file) {
        MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create()

        FileInputStream fileStream = new FileInputStream(file)
        entityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
        entityBuilder.addPart("fileToUpload", new InputStreamBody(fileStream, "application/zip", file.getName()))

        String fileId = null
        String url = API_FILE_UPLOAD
        println "Send POST request to ${this.baseUrl}$url"

        restHelper.removeRequestHeader("Content-Type")  // browser must determine correct multipart entity content type

        def response = restHelper.doPostRequest(url, entityBuilder.build())

        def json = restHelper.parseResponse(response)
        fileId = json.FileId
        assert  fileId != null && fileId.length() >= 10, "The file was NOT uploaded successfully"

        return fileId
    }

    private String fileBasedScan(String fileId, String fileName, ScanType scanType, String parentjobid, String appId) {
        Properties props = new Properties()
        String url = null
        String scanId = null
        String lastExecutionId = null
        String TechName = null

        if (parentjobid != null && !parentjobid.isEmpty()) {
            props.put("FileId", fileId)
            url = String.format(API_RE_SCAN, parentjobid);
            scanId = parentjobid
        }
        else {
            switch (scanType) {
                case ScanType.IOS:
                    url = String.format(MOBILE_API_PATH, API_METHOD_SCANS);
                    props.put("ApplicationFileId", fileId)
                    props.put("ScanName", fileName)
                    TechName = "iOS"
                    break
                case ScanType.Mobile:
                    url = String.format(MOBILE_API_PATH, API_METHOD_SCANS);
                    props.put("ApplicationFileId", fileId)
                    props.put("ScanName", fileName)
                    TechName = "MA Android"
                    break
                case ScanType.SAST:
                    url = String.format(SAST_API_PATH, API_METHOD_SCANS);
                    props.put("ARSAFileId", fileId)
                    props.put("ScanName", fileName)
                    TechName = "StaticAnalyzer"
                    break
                default:
                    assert false , "FileBasedScan does not support scanType $scanType"
            }
        }
        if (appId != null && !appId.isEmpty()) {
            println("APP ID IS " + appId)
            props.put("AppId", appId)
        }

        println "Send POST request to ${this.baseUrl}$url , with the following parameters: $props"
        restHelper.addRequestHeader("Content-Type", "application/json")
        def response = restHelper.doPostRequest(url, props)

        println "Response status line: ${response.getStatusLine()}"
        def json = restHelper.parseResponse(response)

        if (scanId == null) {
            scanId = json.Id
            assert  scanId != null && scanId.length() >= 10, "$TechName scan was NOT started successfully"
            lastExecutionId = json.LatestExecution.Id
        }
        else {
            lastExecutionId = json.Id
        }
        assert  lastExecutionId != null && lastExecutionId.length() >= 10, "$TechName lastExecutionID was NOT started successfully"

        println "$TechName scan was started successfully. scanId=$scanId , executionId=$lastExecutionId"
        return scanId
    }

    /* Verify presence exists and is active */
    protected void verifyPresenceId(String presenceId) {
        if (presenceId == null || presenceId.trim().isEmpty()) {
            return
        }

        println "Waiting for presence to be active. Presence id: " + presenceId

        Long waitTimeout = TimeUnit.MINUTES.toMillis(5)
        Long startTime = System.currentTimeMillis()

        while (true) {
            Thread.sleep(TimeUnit.SECONDS.toMillis(30))

            def presencesData = getPresences()

            println "Current presences: " + presencesData

            //verify that expected presence exists and online
            boolean idExists = presencesData.any( { presence -> return (presence.Id == presenceId && presence.Status == "Active") })

            if (idExists) {
                println "Found active presence with id: " + presenceId
                break;
            }

            assert !(System.currentTimeMillis() - startTime > waitTimeout), "Cannot find active presence with id: " + presenceId
        }
    }

    protected def getPresences() {
        String url = API_PRESENCES

        println "Send GET request to ${this.baseUrl}$url"

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
        String scanType)
    {
        Properties props = new Properties()
        String url = null
        String scanId = null
        String lastExecutionId = null
        String testPolicyForPostRequest;

        switch (testPolicy) {
            case "Application-Only":
                println "Test policy 'Application-Only' will be used."
                testPolicyForPostRequest = "Application-Only.policy"
                break
            case "The Vital Few":
                println "Test policy 'The Vital Few' will be used."
                testPolicyForPostRequest = "The Vital Few.policy"
                break
            case "Default":
                println "Test policy 'Default' will be used."
                testPolicyForPostRequest = "Default.policy"
                break
            default:
                println "Default test policy 'Default.policy' will be used."
                testPolicyForPostRequest = "Default.policy"
        }

        if (parentjobid != null && !parentjobid.isEmpty()) {
            url = String.format(API_RE_SCAN, parentjobid);
            scanId = parentjobid
            props = new Properties()
        }
        else {
            verifyPresenceId(presenceId)
            url = String.format(DAST_API_PATH, API_METHOD_SCANS)

            /* Empty fields are ignored */
            props.putAll([ AppId: appId, ScanName : startingUrl, StartingUrl : startingUrl, LoginUser : loginUsername,
                LoginPassword : loginPassword, ExtraField: thirdCredential, PresenceId : presenceId,
                testPolicy : testPolicyForPostRequest, ScanType: scanType])
        }

        println "Send POST request to ${this.baseUrl}$url , with the following parameters: $props"

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

        assert  lastExecutionId != null && lastExecutionId.length() >= 10, "DAST lastExecutionID was NOT started successfully"
        println "DAST scan was started successfully. scanId=$scanId , executionId=$lastExecutionId"
        return scanId
    }

    public String uploadAPK(File apkFile, String parentjobid, String appId) {
        String fileId = uploadFile(apkFile)
        println "APK file was uploaded successfully. FileID: " + fileId
        return fileBasedScan(fileId, apkFile.getName(), ScanType.Mobile, parentjobid, appId)
    }

    public String uploadARSA(File arsaFile, String parentjobid, String appId) {
        String fileId = uploadFile(arsaFile)
        println "IRX file was uploaded successfully. FileID: " + fileId
        return fileBasedScan(fileId, arsaFile.getName(), ScanType.SAST, parentjobid, appId)
    }

    public String uploadIPAX(File ipaxFile, String appUsername, String appPassword, String parentjobid, String appId) {
        String fileId = uploadFile(ipaxFile)
        println "iOS file was uploaded successfully. FileID: " + fileId
        return fileBasedScan(fileId, ipaxFile.getName(), ScanType.IOS, parentjobid, appId)
    }

    public void waitForScan(String scanId, ScanType scanType, Long scanTimeout, Long startTime, String issueCountString, Properties props) {
        println "Waiting for scan with id '${scanId}'"

        def scan = null
        String status = null
        String executionProgress = null

        Boolean toleratePendingSupport = true;
        String toleratePause = props['toleratePause'];
        if (toleratePause != null && toleratePause.equalsIgnoreCase("false")) {
            toleratePendingSupport = false;
        }

        while (true) {

            Thread.sleep(TimeUnit.MINUTES.toMillis(1))

            scan = getScan(scanId, scanType)
            status = scan.LatestExecution.Status
            executionProgress = scan.LatestExecution.ExecutionProgress
            println "Scan '${scan.Name}' , with id '${scan.Id}' , status is '$status' , ExecutionProgress is '$executionProgress'"
            if (!status.equalsIgnoreCase("Running") || (!toleratePendingSupport && executionProgress.equalsIgnoreCase("Paused"))) {
                break
            }
            assert !(System.currentTimeMillis() - startTime > scanTimeout), "Job running for more than ${TimeUnit.MILLISECONDS.toMinutes(scanTimeout)} minutes"
        }
        assert status.equalsIgnoreCase("Ready"), "Scan status is '$status' and not 'Ready'. (ExecutionProgress is '$executionProgress')"

        downloadReport(scanId, scanType)
        validateScanIssues(scan.LastSuccessfulExecution, scan.Name, scanId, issueCountString)
    }

    private void downloadSingleReportType(String scanId, String reportType) {
        String url =  String.format(API_DOWNLOAD_REPORT, scanId, reportType)

        println "Send GET request to ${this.baseUrl}$url"

        def response = restHelper.doGetRequest(url)

        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        response.getEntity().writeTo(baos)
        byte[] bytes = baos.toByteArray()

        println "Response status line: ${response.statusLine}"
        assert bytes.length > RestClient.MinReportSize, "Report size '${bytes.length}' is invalid"
        println "Report type ${reportType} downloaded successfully"
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
            case ScanType.Mobile:
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

    public void deleteAllPresences() {
        println "Deleting all existing presences"

        String url = API_PRESENCES

        def presencesData = getPresences()

        println "Current presences: " + presencesData

        presencesData.each { presence ->
            println "Deleting presence with id: " + presence.Id

            HttpResponse response = restHelper.doPostRequest(url + "/" + presence.Id)
        }
    }

    public String createNewPresence() {
        println "Creating new presence"

        Date currentTime = new Date();

        String url = API_PRESENCES

        println "Send POST request to ${this.baseUrl}$url"

        Properties props = new Properties()

        props.put("PresenceName", "Groovy Presence" + currentTime)
        def response = restHelper.doPostRequest(url, props)

        def json = restHelper.parseResponse(response)
        String presenceId = json.Id

        return presenceId
    }

    public void renewPresenceKeyFile(String serviceDirectory, String presenceId) {
        println "Downloading new key file for presence with id: ${presenceId}"

        String url = API_PRESENCES + "/" + presenceId + "/" + NEW_KEY

        println "Send POST request to ${this.baseUrl}$url"
        HttpResponse response = restHelper.doPostRequest(url, null)

        println "Response status line: ${response.statusLine}"

        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        response.getEntity().writeTo(baos)
        byte[] bytes = baos.toByteArray()

        File keyFile = new File(serviceDirectory, "AppScanPresence.key")
        try {
            boolean deleted = keyFile.delete();
            if (deleted) {
                println "Deleted presence key file ${keyFile.getAbsolutePath()}"
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
        println "Downloading latest Appscan Presence"

        String url = API_PRESENCES + "/${presenceId}/Download" + (isWindows ? Win_x86_64 : Linux_x86_64)

        File serviceDir = new File(serviceDirectory)

        println "Send POST request to ${this.baseUrl}$url"
        restHelper.addRequestHeader("Accept", "application/zip")
        HttpResponse response = restHelper.doPostRequest(url, null)

        InputStream zip = response.getEntity().getContent()
        serviceDir.deleteDir()
        unzip(zip, "", serviceDir.getName())
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
        println "Verifying domain for url: " + urlToVerify

        String url = API_DOMAIN_OWNERSHIP + Verify

        Properties props = new Properties()
        props.put("STP", urlToVerify)
        println "Send POST request to ${this.baseUrl}$url: $props"
        HttpResponse response = restHelper.doPostRequest(url, props)

        println "Response status line: ${resp.statusLine}"

        String responseText = response.entity.content.text
        println "Response for url verification is: " + responseText

        boolean result = Boolean.parseBoolean(responseText)

        return result
    }

    public void registerEmailDomain(String urlToRegister, AdminMailPrefix mailPrefix, String domainToRegister) {
        println "Registering Email domain for url '${urlToRegister}' and domain '${domainToRegister}' with prefix '${mailPrefix}'"

        String url = API_DOMAIN_OWNERSHIP + Register + DORegistrationType.Email

        Properties props = new Properties()
        props.putAll([stp: urlToRegister, mailprefix: mailPrefix, domain: domainToRegister])
        println "Send POST request to ${this.baseUrl}$url: $props"
        HttpResponse response = restHelper.doPostRequest(url, props)
        println "Response status line: ${response.statusLine}"

        String responseText = response.entity.content.text
        println "Response for Email registration is: " + responseText
    }

    public File registerHtmlDomain(String urlToRegister, String domainToRegister) {
        println "Registering Html domain for url '${urlToRegister}' and domain '${domainToRegister}'"

        String url = API_DOMAIN_OWNERSHIP + Register + DORegistrationType.Html

        Properties props = new Properties()
        props.putAll([stp: urlToRegister, domain: domainToRegister])
        println "Send POST request to ${this.baseUrl}$url: $props"

        File domainVerificationHtml = null

        HttpResponse response = restHelper.doPostRequest(url, props)

        println "Response status line: ${response.statusLine}"

        domainVerificationHtml = new File("IBMDomainVerification.html")

        try {
            if (domainVerificationHtml.exists()) {
                println "Found existing html file, deleting"
                domainVerificationHtml.delete()
            }
        } catch (Exception e) {
            println "Failed deleting old html file ${domainVerificationHtml.getAbsolutePath()} with exception: ${e.getMessage()}"
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        response.getEntity().writeTo(baos)
        byte[] bytes = baos.toByteArray()

        println "Writing ${bytes.length} bytes to html file"

        (domainVerificationHtml).withOutputStream {
            it.write(bytes)
        }

        return domainVerificationHtml
    }

    public void confirmDomainEmail(String verificationToken) {
        println "Confirming Email domain with token: " + verificationToken

        String url = API_DOMAIN_OWNERSHIP + Confirm + verificationToken

        println "Send GET request to ${this.baseUrl}$url"
        HttpResponse response = restHelper.doGetRequest(url)
        println "Response status line: ${response.statusLine}"

        assert response.containsHeader("emailVerified"), "Failed confirming domain"
    }

    protected abstract def login()
}
