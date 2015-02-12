/**
 * © Copyright IBM Corporation 2015.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

package com.urbancode.air.plugin.AppScanSaaS

import groovy.json.JsonSlurper
import groovyx.net.http.*

import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.content.InputStreamBody
import org.apache.http.entity.mime.content.StringBody

public abstract class RestClient {
	protected boolean validateSSL;
	protected String token
	protected String authHeader

	protected String baseUrl
	protected String username
	protected String password
	protected HTTPBuilder httpBuilder

	protected String apiEnvironment;
	
	private static final String MOBILE_API_PATH = "/api/%s/MobileAnalyzer/"
	private static final String SAST_API_PATH = "/api/%s/StaticAnalyzer/"
	private static final String DAST_API_PATH = "/api/%s/DynamicAnalyzer/"
	private static final String IOS_API_PATH = "/api/%s/iOS/"
	private static final String API_METHOD_SCAN = "Scan"
	private static final String API_METHOD_DOWNLOAD_REPORT = "DownloadReport"
	private static final String API_METHOD_SCANS = "Scans"
	private static final String API_DOWNLOAD_LIBRARY = "DownloadLibrary"
	public static final String REPORT_DIR = "Report"

	public RestClient(Properties props, boolean validateSSL) {
		this.validateSSL = validateSSL;
		String userServer = props.containsKey("userServer") ? props["userServer"] : "appscan.bluemix.net";
		String userServerPort = props.containsKey("userServerPort") ? props["userServerPort"] : "443";
		
		this.baseUrl = "https://" + userServer + ":" +  userServerPort
		this.username = props["loginUsername"]
		this.password = props["loginPassword"]
		this.httpBuilder = initializeHttpBuilder(baseUrl)
		login()		
	}

	public String uploadAPK(File apkFile, String parentjobid) {
		FileInputStream apkStream = new FileInputStream(apkFile)

		MultipartEntity multiPartContent = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE)
		multiPartContent.addPart("ContentApk", new InputStreamBody(apkStream, "application/zip", apkFile.getName()))
		multiPartContent.addPart("ScanName", new StringBody(apkFile.getName()))
		multiPartContent.addPart("userAgreeToPay", new StringBody("true"))
		
		if (parentjobid != null && !parentjobid.isEmpty()) {
			multiPartContent.addPart("parentjobid", new StringBody(parentjobid))
		}

		String apiPath = getApiPath(ScanType.Mobile);
		String url = "${apiPath}${API_METHOD_SCAN}"
		println "Send POST request to ${this.baseUrl}$url"

		String scanId = null

		httpBuilder.request(Method.POST){ req ->
			uri.path = url
			headers["Authorization"] = authHeader
			requestContentType: "multipart/form-data"
			req.setEntity(multiPartContent)

			response.success = { HttpResponseDecorator resp, json ->
				println "Response status line: ${resp.statusLine}"

				assert resp.statusLine.statusCode == 201, "APK was not uploaded successfuly"
				scanId = json.JobId
				assert  scanId != null && scanId.length() >= 10, "APK was not uploaded successfuly"
			}
		}

		println "APK was uploaded successfuly. Job Id: " + scanId

		return scanId
	}
	
	public String uploadARSA(File arsaFile, String parentjobid) {
		FileInputStream arsaStream = new FileInputStream(arsaFile)

		MultipartEntity multiPartContent = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE)
		multiPartContent.addPart("ContentArsa", new InputStreamBody(arsaStream, "application/zip", arsaFile.getName()))
		multiPartContent.addPart("ScanName", new StringBody(arsaFile.getName()))
		multiPartContent.addPart("userAgreeToPay", new StringBody("true"))
		
		if (parentjobid != null && !parentjobid.isEmpty()) {
			multiPartContent.addPart("parentjobid", new StringBody(parentjobid))
		}

		String apiPath = getApiPath(ScanType.SAST);
		String url = "${apiPath}${API_METHOD_SCAN}"
		println "Send POST request to ${this.baseUrl}$url"

		String scanId = null

		httpBuilder.request(Method.POST){ req ->
			uri.path = url
			headers["Authorization"] = authHeader
			requestContentType: "multipart/form-data"
			req.setEntity(multiPartContent)

			response.success = { HttpResponseDecorator resp, json ->
				println "Response status line: ${resp.statusLine}"

				assert resp.statusLine.statusCode == 201, "ARSA was not uploaded successfuly"
				scanId = json.JobId
				assert  scanId != null && scanId.length() >= 10, "ARSA was not uploaded successfuly"
			}
		}

		println "Arsa was uploaded successfuly. Job Id: " + scanId

		return scanId
	}
	
	
	public String uploadIPAX(File ipaFile, String appUsername, String appPassword, String parentjobid) {
		File ipaxFile = getIPAXFile(ipaFile);
		FileInputStream ipaxStream = new FileInputStream(ipaxFile)

		MultipartEntity multiPartContent = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE)
		multiPartContent.addPart("ContentIPAX", new InputStreamBody(ipaxStream, "application/zip", ipaxFile.getName()))
		multiPartContent.addPart("ScanName", new StringBody(ipaFile.getName()))
		multiPartContent.addPart("userAgreeToPay", new StringBody("true"))
		
		if (appUsername != null) {
			multiPartContent.addPart("appUsername", new StringBody(appUsername))
		}
		if (appPassword != null) {
			multiPartContent.addPart("appPassword", new StringBody(appPassword))
		}
		if (parentjobid != null && !parentjobid.isEmpty()) {
			multiPartContent.addPart("parentjobid", new StringBody(parentjobid))
		}

		String apiPath = getApiPath(ScanType.IOS);
		String url = "${apiPath}${API_METHOD_SCAN}"
		println "Send POST request to ${this.baseUrl}$url"

		String scanId = null

		httpBuilder.request(Method.POST){ req ->
			uri.path = url
			headers["Authorization"] = authHeader
			requestContentType: "multipart/form-data"
			req.setEntity(multiPartContent)

			response.success = { HttpResponseDecorator resp, json ->
				println "Response status line: ${resp.statusLine}"

				assert resp.statusLine.statusCode == 201, "IPA was not uploaded successfuly"
				scanId = json.JobId
				assert  scanId != null && scanId.length() >= 10, "IPA was not uploaded successfuly"
			}
		}

		println "IPA was uploaded successfuly. Job Id: " + scanId

		return scanId
	}
	
	protected File getIPAXFile(File ipaFile) {
		//the server supports only IPAX file (Zip file with IPA and version.txt inside so we need to simulate it)
		String apiPath = getApiPath(ScanType.IOS);
		String url = "${apiPath}${API_DOWNLOAD_LIBRARY}"
		
		println "Send GET request to ${this.baseUrl}$url"
		
		def libraryWrapper = null
		
		httpBuilder.request(Method.GET){ req ->
				uri.path = url
				headers["Authorization"] = authHeader
		
				response.success = { HttpResponseDecorator resp, json ->
					println "Response status line: ${resp.statusLine}"
		
					libraryWrapper = json
					assert resp.statusLine.statusCode == 200, "Could not download iOS library"
				}
			}
		
		String version = libraryWrapper.VersionNumber
		println "iOS library retreived successfuly. Version number is: ${version}"
		
		File versionFile = new File("version.txt")
		versionFile.write(version)
		
		File ipaxFile = new File(ipaFile.getName().replaceAll("\\.ipa\$", "") + ".ipax")
		ZipOutputStream zipFile = new ZipOutputStream(new FileOutputStream(ipaxFile.getName()))
		
		[ versionFile, ipaFile ].each { 
			zipFile.putNextEntry(new ZipEntry(it.name))
			it.withInputStream { i ->
				zipFile << i
				}
			
			zipFile.closeEntry()
		}
		
		zipFile.finish()
		
		return ipaxFile;
	} 


	public String startDastScan(String startingUrl, String loginUsername, String loginPassword, String parentjobid){
		Map<String, Serializable> params = [ ScanName : startingUrl, StartingUrl : startingUrl, 
											 LoginUser : loginUsername, LoginPassword : loginPassword, 
											 userAgreeToPay : true, parentjobid : parentjobid]

		String apiPath = getApiPath(ScanType.DAST);
		String url = "${apiPath}${API_METHOD_SCAN}"
		println "Send POST request to ${this.baseUrl}$url: ${params}"

		String scanId = null

		httpBuilder.request(Method.POST){ req ->
			uri.path = url
			headers["Authorization"] = authHeader
			requestContentType = ContentType.URLENC
			body = params

			response.success = { HttpResponseDecorator resp, json ->
				println "Response status line: ${resp.statusLine}"

				assert resp.statusLine.statusCode == 201, "DAST was not started successfuly"

				scanId = json.JobId
				assert scanId != null && scanId.length() >= 10, "DAST was not started successfuly"
			}
		}

		println "DAST scan was started successfuly. Job Id: " + scanId

		return scanId
	}

	public void waitForScan(String scanId, ScanType scanType, Long scanTimeout, Long startTime, String issueCountString) {
		println "Waiting for scan with id '${scanId}'"

		int iterations = 0
		while (true) {
			iterations++

			Thread.sleep(TimeUnit.MINUTES.toMillis(1))

			def scan = getScan(scanId, scanType)

			println "Scan '${scan.Name}' with id '${scan.JobId}' status is '${scan.JobStatus}'"

			if (scan.JobStatus >= 3) {
				break
			}
			
			assert !(System.currentTimeMillis() - startTime > scanTimeout), "Job running for more than ${TimeUnit.MILLISECONDS.toMinutes(scanTimeout)} minutes"
		}

		downloadReport(scanId, scanType)
		validateScanIssues(scanId, scanType, issueCountString)
	}

	public def getScan(String scanId, ScanType scanType) {
		String path = getApiPath(scanType)

		String url = "${path}${API_METHOD_SCAN}/${scanId}"
		println "Send GET request to ${this.baseUrl}$url"

		def scan = null

		httpBuilder.request(Method.GET){ req ->
			uri.path = url
			headers["Authorization"] = authHeader

			response.success = { HttpResponseDecorator resp, json ->
				println "Response status line: ${resp.statusLine}"

				scan = json
				assert resp.statusLine.statusCode == 200, "Scan was not retreived successfuly"
			}
		}

		println "Scan retreived successfuly. Scan name is: ${scan.Name}"

		return scan
	}

	public void downloadReport(String scanId, ScanType scanType) {
		String path = getApiPath(scanType)

		String url = "${path}${API_METHOD_DOWNLOAD_REPORT}/${scanId}/zip"
		println "Send POST request to ${this.baseUrl}$url"

		httpBuilder.request(Method.POST, ContentType.BINARY){ req ->
			uri.path = url
			requestContentType = ContentType.URLENC

			headers["Authorization"] = authHeader
			headers["Accept"] = "application/zip"

			response.success = { HttpResponseDecorator resp, zip ->
				println "Response status line: ${resp.statusLine}"

				assert resp.statusLine.statusCode == 200, "Report was not downloaded successfuly"

				unzip(zip, "(\\.pdf|\\.xml)\$", REPORT_DIR)

				assert (new File(REPORT_DIR + File.separator + "${scanId}-final.xml")).exists(), "Xml report file is missing"
				assert (new File(REPORT_DIR + File.separator + "${scanId}.pdf")).exists(), "Pdf report file is missing"
			}
		}

		println "Report downloaded successfuly"
	}
	
	protected void validateScanIssues(String scanId, ScanType scanType, String issueCountString) {
		if (issueCountString == null || issueCountString.isEmpty()) {
			return
		}
		
		String[] issueCount = issueCountString.split(",");
		
		int maxHighSeverityIssues = issueCount.length > 0 ? Integer.parseInt(issueCount[0]) : Integer.MAX_VALUE;
		int maxMediumSeverityIssues = issueCount.length > 1 ? Integer.parseInt(issueCount[1]) : Integer.MAX_VALUE;
		int maxLowSeverityIssues = issueCount.length > 2 ? Integer.parseInt(issueCount[2]) : Integer.MAX_VALUE;
		int maxInformationalSeverityIssues = issueCount.length > 3 ? Integer.parseInt(issueCount[3]) : Integer.MAX_VALUE;
		
		def scan = getScan(scanId, scanType)
						
		println "Scan ${scan.Name} with id ${scanId} has ${scan.NHighIssues} high severity issues"
		assert scan.NHighIssues <= maxHighSeverityIssues, "Scan failed to meet high issue count. Result: ${scan.NHighIssues}. Max expected: ${maxHighSeverityIssues}"
		
		println "Scan ${scan.Name} with id ${scanId} has ${scan.NMediumIssues} medium severity issues"
		assert scan.NMediumIssues <= maxMediumSeverityIssues, "Scan failed to meet medium issue count. Result: ${scan.NMediumIssues}. Max expected: ${maxMediumSeverityIssues}"
		
		println "Scan ${scan.Name} with id ${scanId} has ${scan.NLowIssues} low severity issues"
		assert scan.NLowIssues <= maxLowSeverityIssues, "Scan failed to meet low issue count. Result: ${scan.NLowIssues}. Max expected: ${maxLowSeverityIssues}"
		
		println "Scan ${scan.Name} with id ${scanId} has ${scan.NInfoIssues} informational severity issues"
		assert scan.NInfoIssues <= maxInformationalSeverityIssues, "Scan failed to meet informational issue count. Result: ${scan.NInfoIssues}. Max expected: ${maxInformationalSeverityIssues}"
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

	public def getAllScans(ScanType scanType) {
		String path = getApiPath(scanType)

		String url = "${path}${API_METHOD_SCANS}";
				
		println "Send GET request to ${this.baseUrl}$url"

		def scans = null

		httpBuilder.request(Method.GET){ req ->
			uri.path = url
			headers["Authorization"] = authHeader

			response.success = { HttpResponseDecorator resp, json ->
				println "Response status line: ${resp.statusLine}"

				scans = json
				assert resp.statusLine.statusCode == 200, "User scans were not retreived successfuly"
			}
		}

		println "User scans retreived successfuly"

		return scans
	}

	public void deleteScan(String scanId, ScanType scanType) {
		String path = getApiPath(scanType)

		String url = "${path}${API_METHOD_SCAN}/${scanId}"
		println "Send DELETE request to ${this.baseUrl}$url"

		httpBuilder.request(Method.DELETE){ req ->
			uri.path = url
			headers["Authorization"] = authHeader

			response.success = { HttpResponseDecorator resp ->
				println "Response status line: ${resp.statusLine}"

				assert resp.statusLine.statusCode == 204, "Failed deleting scan with id: ${scanId}"
			}
		}
	}
	
	protected String getApiPath(ScanType scanType) {
		String apiPathFormat = null;
		switch (scanType) {
			case ScanType.Mobile:
				apiPathFormat = MOBILE_API_PATH;
				break
			case ScanType.SAST:
				apiPathFormat = SAST_API_PATH;
				break
			case ScanType.IOS:
				apiPathFormat = IOS_API_PATH;
				break
			default:
				apiPathFormat = DAST_API_PATH;
		}
		return String.format(apiPathFormat, apiEnvironment);
	}
	
	protected abstract def login();

	protected void assertLogin() {
		assert token != null, "Login failed. Token is null"
		authHeader = "Bearer " + token
	}

	protected void bluemixLogin() {
		token = null
		authHeader = null
		apiEnvironment = "BlueMix";
		
		Map<String, Serializable> params = [ Bindingid : username, Password : password]
				
		String url = String.format("/api/%s/Account/BMAPILogin", apiEnvironment);
		println "Send POST request to ${baseUrl}$url: $params"

		httpBuilder = initializeHttpBuilder(baseUrl)
		httpBuilder.request(Method.POST){ req ->
			uri.path = url
			requestContentType = ContentType.URLENC
			body = params

			response.success = { HttpResponseDecorator resp, json ->
				println "Response status line: ${resp.statusLine}"
				assert resp.status == 200, "Login failed"

				token = json.Token
				println "External Token: ${token}"
			}
			response.failure = { resp ->
				println 'Bluemix authentication failed.'				
			}
		}
	}
	
	protected void scxLogin() {
		token = null
		authHeader = null
		apiEnvironment = "SCX";
		
		Map<String, Serializable> params = [ Username : username, Password : password]
				
		String url = String.format("/api/%s/Account/LoginUsingIBMID", apiEnvironment);
		println "Send POST request to ${baseUrl}$url: $params"

		httpBuilder = initializeHttpBuilder(baseUrl)
		httpBuilder.request(Method.POST){ req ->
			uri.path = url
			requestContentType = ContentType.URLENC
			body = params

			response.success = { HttpResponseDecorator resp, json ->
				println "Response status line: ${resp.statusLine}"
				assert resp.status == 200, "Login failed"

				token = json.Token
				println "External Token: ${token}"
			}
			response.failure = { resp ->
				println 'SCX authentication failed.'
			}
		}
	}

	protected HTTPBuilder initializeHttpBuilder(String baseUrl) {
		HTTPBuilder httpBuilder = new HTTPBuilder(baseUrl)

		if (!validateSSL) {
			HttpBuilderUtils.ignoreSSLErrors(httpBuilder)
		}

		return httpBuilder
	}
}
