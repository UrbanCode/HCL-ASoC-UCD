/**
 * © Copyright IBM Corporation 2015.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

package com.urbancode.air.plugin.AppScanSaaS

import groovy.json.JsonSlurper
import groovyx.net.http.*

import java.io.InputStream;
import java.util.Formatter.DateTime;
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.content.InputStreamBody
import org.apache.http.entity.mime.content.StringBody

public abstract class RestClient extends RestClientBase {

	private static final String API_V2 = "/api/v2/%s"
	private static final String API_BLUEMIX_LOGIN = "/api/V2/Account/BluemixLogin"
	private static final String API_IBMID_LOGIN = "/api/V2/Account/IBMIdLogin"
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
	private static final String NEW_KEY = "NewKey"
	
	private static final String API_TOOLS = "/api/v2/Tools"
	private static final String ASPresence = "/ASPresence"
	private static final String Linux_x86_64 = "/Linux_x86_64"
	private static final String Win_x86_64 = "/Win_x86_64"

	private static final int MinReportSize = 1024
	
	public RestClient(Properties props, boolean validateSSL, boolean useAsmGatewayAsDefault) {
		super(props, validateSSL, useAsmGatewayAsDefault)
	}
	
	private String uploadFile(File file) {
		FileInputStream fileStream = new FileInputStream(file)
		MultipartEntity multiPartContent = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE)
		multiPartContent.addPart("fileToUpload", new InputStreamBody(fileStream, "application/zip", file.getName()))

		String fileId = null
		String url = API_FILE_UPLOAD
		println "Send POST request to ${this.baseUrl}$url"
		httpBuilder.request(Method.POST, ContentType.JSON){ req ->
			uri.path = url
			headers["Authorization"] = authHeader
			requestContentType: "multipart/form-data"
			req.setEntity(multiPartContent)

			response.success = { HttpResponseDecorator resp ->
				println "Response status line: ${resp.statusLine}"

				assert resp.statusLine.statusCode == 201, "The file was NOT uploaded successfully"
				def json = parseJsonFromResponseText(resp, "The file was NOT uploaded successfully")
				fileId = json.FileId
				assert  fileId != null && fileId.length() >= 10, "The file was NOT uploaded successfully"
			}
		}
		return fileId
	}
	
	private String fileBasedScan(String fileId, String fileName, ScanType scanType, String parentjobid) {
		Map<String, Serializable> params = null
		String url = null
		String scanId = null
		String lastExecutionId = null
		String TechName = null
		
		if (parentjobid != null && !parentjobid.isEmpty()) {
			params = [ FileId : fileId]
			url = String.format(API_RE_SCAN, parentjobid);
			scanId = parentjobid
		}
		else {
			switch (scanType) {
				case ScanType.IOS:
					url = String.format(MOBILE_API_PATH, API_METHOD_SCANS);
					params = [ ApplicationFileId : fileId, ScanName : fileName]
					TechName = "iOS"
					break
				case ScanType.Mobile:
					url = String.format(MOBILE_API_PATH, API_METHOD_SCANS);
					params = [ ApplicationFileId : fileId, ScanName : fileName]
					TechName = "MA Android"
					break
				case ScanType.SAST:
					url = String.format(SAST_API_PATH, API_METHOD_SCANS);
					params = [ ARSAFileId : fileId, ScanName : fileName]
					TechName = "StaticAnalyzer"
					break
				default:
					assert false , "FileBasedScan does not support scanType $scanType"
			}
		}
		println "Send POST request to ${this.baseUrl}$url"
		httpBuilder.request(Method.POST, ContentType.JSON){ req ->
			uri.path = url
			headers["Authorization"] = authHeader
			requestContentType = ContentType.JSON
			body = params
			
			response.success = { HttpResponseDecorator resp ->
				println "Response status line: ${resp.statusLine}"
				assert resp.statusLine.statusCode == 201, "$TechName scan was NOT started successfully"
				def json = parseJsonFromResponseText(resp, "fileBasedScan was NOT started successfully")
				if (scanId == null) {
					scanId = json.Id
					assert  scanId != null && scanId.length() >= 10, "$TechName scan was NOT started successfully"
					lastExecutionId = json.LatestExecution.Id
				}
				else {
					lastExecutionId = json.Id
				}
				assert  lastExecutionId != null && lastExecutionId.length() >= 10, "$TechName lastExecutionID was NOT started successfully"
			}
		}
		println "$TechName scan was started successfully. scanId=$scanId , executionId=$lastExecutionId"
		return scanId
	}
	
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
		
		httpBuilder.request(Method.GET, ContentType.JSON){ req ->
			uri.path = url
			headers["Authorization"] = authHeader
			
			response.success = { HttpResponseDecorator resp ->
				println "Response status line: ${resp.statusLine}"
				
				assert resp.statusLine.statusCode == 200, "Get Presences failed"
				presencesData = parseJsonFromResponseText(resp, "Get Presences failed")
			}
		}
		
		return presencesData
	}
	
	@Override
	public String startDastScan(String startingUrl, String loginUsername, String loginPassword, String parentjobid, String presenceId) {
		Map<String, Serializable> params = null
		String url = null
		String scanId = null
		String lastExecutionId = null
		
		if (parentjobid != null && !parentjobid.isEmpty()) {
			url = String.format(API_RE_SCAN, parentjobid);
			scanId = parentjobid
		}
		else {
			verifyPresenceId(presenceId)
			
			params = [ ScanName : startingUrl, StartingUrl : startingUrl,
						LoginUser : loginUsername, LoginPassword : loginPassword, PresenceId : presenceId]
			url = String.format(DAST_API_PATH, API_METHOD_SCANS);
		}
		
		println "Send POST request to ${this.baseUrl}$url"
		httpBuilder.request(Method.POST, ContentType.JSON){ req ->
			uri.path = url
			headers["Authorization"] = authHeader
			requestContentType = ContentType.JSON
			body = params
			
			response.success = { HttpResponseDecorator resp ->
				println "Response status line: ${resp.statusLine}"
				assert resp.statusLine.statusCode == 201, "DAST scan was NOT started successfully"
				def json = parseJsonFromResponseText(resp, "DAST scan was NOT started successfully")
				if (scanId == null) {
					scanId = json.Id
					assert  scanId != null && scanId.length() >= 10, "DAST scan was NOT started successfully"
					lastExecutionId = json.LatestExecution.Id
				}
				else {
					lastExecutionId = json.Id
				}
				assert  lastExecutionId != null && lastExecutionId.length() >= 10, "DAST lastExecutionID was NOT started successfully"
			}
		}
		println "DAST scan was started successfully. scanId=$scanId , executionId=$lastExecutionId"
		return scanId
	}
	
	public String uploadAPK(File apkFile, String parentjobid) {
		String fileId = uploadFile(apkFile)
		println "APK file was uploaded successfully. FileID: " + fileId
		return fileBasedScan(fileId, apkFile.getName(), ScanType.Mobile, parentjobid)
	}
	
	public String uploadARSA(File arsaFile, String parentjobid) {
		String fileId = uploadFile(arsaFile)
		println "IRX file was uploaded successfully. FileID: " + fileId
		return fileBasedScan(fileId, arsaFile.getName(), ScanType.SAST, parentjobid)
	}
	
	public String uploadIPAX(File ipaxFile, String appUsername, String appPassword, String parentjobid) {
		String fileId = uploadFile(ipaxFile)
		println "IPAX file was uploaded successfully. FileID: " + fileId
		return fileBasedScan(fileId, ipaxFile.getName(), ScanType.IOS, parentjobid)
	}
	
	@Override
	public void waitForScan(String scanId, ScanType scanType, Long scanTimeout, Long startTime, String issueCountString) {
		println "Waiting for scan with id '${scanId}'"
		
		def scan = null
		String status = null
		
		while (true) {			
			
			Thread.sleep(TimeUnit.MINUTES.toMillis(1))
			
			scan = getScan(scanId, scanType)
			status = scan.LatestExecution.Status
			println "Scan '${scan.Name}' with id '${scan.Id}' status is '$status'"
			if (!status.equalsIgnoreCase("Running")) {
				break
			}
			assert !(System.currentTimeMillis() - startTime > scanTimeout), "Job running for more than ${TimeUnit.MILLISECONDS.toMinutes(scanTimeout)} minutes"
		}
		assert status.equalsIgnoreCase("Ready"), "Scan status is '$status' and not 'Ready'"

		downloadReport(scanId, scanType)
		validateScanIssues(scan.LastSuccessfulExecution, scan.Name, scanId, issueCountString)
	}
	
	private void downloadSingleReportType(String scanId, String reportType) {
		String url =  String.format(API_DOWNLOAD_REPORT, scanId, reportType)
		
		println "Send GET request to ${this.baseUrl}$url"
		httpBuilder.request(Method.GET, ContentType.BINARY){ req ->
			uri.path = url
			headers["Authorization"] = authHeader

			response.success = { HttpResponseDecorator resp ->
				ByteArrayOutputStream baos = new ByteArrayOutputStream()
				resp.getEntity().writeTo(baos)
				byte[] bytes = baos.toByteArray()
				
				println "Response status line: ${resp.statusLine}"
				assert bytes.length > RestClient.MinReportSize, "Report size '${bytes.length}' is invalid"
				assert resp.statusLine.statusCode == 200 , "Report type ${reportType} was not retrieved successfully"
			}
		}
		println "Report type ${reportType} downloaded successfully"
	}

	public void downloadReport(String scanId, ScanType scanType) {
		downloadSingleReportType(scanId, REPORT_TYPE_XML)
		if (scanType == ScanType.SAST) {
			downloadSingleReportType(scanId, REPORT_TYPE_HTML)
		}
		else {
			downloadSingleReportType(scanId, REPORT_TYPE_PDF)
			if (scanType == ScanType.DAST) {
				downloadSingleReportType(scanId, REPORT_TYPE_COMPLIANCE_PDF)
			}
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

	@Override
	protected String getScanUrl(String scanId, ScanType scanType)
	{
		String path = String.format(getApiPath(scanType), API_METHOD_SCANS)
		return "${path}/${scanId}"
	}
	
	@Override
	protected def getAllScansUrl(ScanType scanType)
	{
		return String.format(API_V2, API_METHOD_SCANS)
	}
	
	@Override
	protected String getDeleteScanUrl(String scanId, ScanType scanType)
	{
		return String.format(API_V2, API_METHOD_SCANS).concat("/${scanId}")
	}
	
	@Override
	protected String getBluemixLoginUrl()
	{
		return API_BLUEMIX_LOGIN
	}

	@Override
	protected String getScxLoginUrl()
	{
		return API_IBMID_LOGIN
	}
	
	public void deleteAllPresences() {
		println "Deleting all existing presences"
		
		String url = API_PRESENCES
		
		def presencesData = getPresences()
		
		println "Current presences: " + presencesData
		
		presencesData.each { presence -> 
			println "Deleting presence with id: " + presence.Id
			httpBuilder.request(Method.DELETE, ContentType.JSON) { req -> 
				uri.path = url + "/" + presence.Id
				headers["Authorization"] = authHeader
				
				response.success = { HttpResponseDecorator resp ->
					println "Response status line: ${resp.statusLine}"
					
					assert resp.statusLine.statusCode == 204, "Delete presence failed for id: " + presence.Id
					presencesData = parseJsonFromResponseText(resp, "Delete presence failed for id: " + presence.Id)
				}
			}
		}
	}
	
	public String createNewPresence() {
		println "Creating new presence"
		
		Date currentTime = new Date();
		
		String url = API_PRESENCES
		
		println "Send POST request to ${this.baseUrl}$url"
		
		String presenceId = null
		
		httpBuilder.request(Method.POST, ContentType.JSON){ req ->
			uri.path = url
			headers["Authorization"] = authHeader
			requestContentType = ContentType.JSON
			body = [ PresenceName : "Grovvy Presence: " + currentTime]
			
			response.success = { HttpResponseDecorator resp ->
				println "Response status line: ${resp.statusLine}"
				
				assert resp.statusLine.statusCode == 201, "Failed creating new presence"
				def json = parseJsonFromResponseText(resp, "Failed creating new presence")
				presenceId = json.Id
			}
		}
		
		return presenceId
	}
	
	public void downloadPresenceKeyFile(String serviceDirectory, String presenceId) {
		println "Downloading new key file for presence with id: ${presenceId}"
		
		String url = API_PRESENCES + "/" + presenceId + "/" + NEW_KEY

		println "Send POST request to ${this.baseUrl}$url"
		
		httpBuilder.request(Method.POST, ContentType.BINARY){ req ->
			uri.path = url
			headers["Authorization"] = authHeader
			
			response.success = { HttpResponseDecorator resp ->
				println "Response status line: ${resp.statusLine}"							
				
				assert resp.statusLine.statusCode == 200, "Generate presence key failed"				

				ByteArrayOutputStream baos = new ByteArrayOutputStream()
				resp.getEntity().writeTo(baos)
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
		}				
	}
	
	public void downloadAppscanPresence(String serviceDirectory, boolean isWindows) {
		println "Downloading latest Appscan Presence"
		
		String url = API_TOOLS + ASPresence + (isWindows ? Win_x86_64 : Linux_x86_64)
		
		File serviceDir = new File(serviceDirectory)		
		
		println "Send GET request to ${this.baseUrl}$url"
		
		httpBuilder.request(Method.GET){ req ->
			uri.path = url
			headers["Authorization"] = authHeader
			headers["Accept"] = "application/zip"
			response.success = { HttpResponseDecorator resp, zip ->
				println "Response status line: ${resp.statusLine}"

				assert resp.statusLine.statusCode == 200, "Failed downloading Appscan Presence tool"
				println "Appscan Presence tool successfully received. Deleting ${serviceDir.getName()} before extracting it."
				
				serviceDir.deleteDir()
				unzip(zip, "", serviceDir.getName())
			}
		}		
	}
}
