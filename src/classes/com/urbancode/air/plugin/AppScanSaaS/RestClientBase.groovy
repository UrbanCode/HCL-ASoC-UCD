/**
 * © Copyright IBM Corporation 2015.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

package com.urbancode.air.plugin.AppScanSaaS

import groovy.json.JsonSlurper
import groovyx.net.http.*

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.content.InputStreamBody
import org.apache.http.entity.mime.content.StringBody
import org.apache.http.conn.ssl.SSLContextBuilder
import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients;

public abstract class RestClientBase {
	
	protected boolean validateSSL;
	protected String token
	protected String authHeader

	protected String baseUrl
	protected String username
	protected String password
	protected String apiEnvironment;

	protected HTTPBuilder httpBuilder
	
	protected static final String ANALYZERS_API_BM_DOMAIN = "appscan.bluemix.net"
	protected static final String ASM_API_GATEWAY_DOMAIN = "appscan.ibmcloud.com"
	protected static final String MOBILE_API_PATH_V1 = "/api/%s/MobileAnalyzer/"
	protected static final String SAST_API_PATH_V1 = "/api/%s/StaticAnalyzer/"
	protected static final String DAST_API_PATH_V1 = "/api/%s/DynamicAnalyzer/"
	protected static final String IOS_API_PATH_V1 = "/api/%s/iOS/"
	
	protected static final String API_METHOD_DOWNLOAD_TOOL_V1 = "DownloadTool"
	
	public RestClientBase(Properties props, boolean validateSSL, boolean useAsmGatewayAsDefault) {
		this.validateSSL = validateSSL;
		String userServer = props.containsKey("userServer") ? props["userServer"] : (useAsmGatewayAsDefault ? ASM_API_GATEWAY_DOMAIN : ANALYZERS_API_BM_DOMAIN);
		String userServerPort = props.containsKey("userServerPort") ? props["userServerPort"] : "443";
		
		this.baseUrl = "https://" + userServer + ":" +  userServerPort
		this.username = props["loginUsername"]
		this.password = props["loginPassword"]
		this.httpBuilder = initializeHttpBuilder(baseUrl)
		login()		
	}

	public getBaseUrl() {
		return this.baseUrl
	}
	
	public File getIPAXGenerator() {
		String apiPath = getApiPath_V1(ScanType.IOS);
		String url = "${apiPath}${API_METHOD_DOWNLOAD_TOOL_V1}"
		String dirName = "IPAX_Generator_for_${this.username}"
		println "Send POST request to ${this.baseUrl}$url"
		
		httpBuilder.request(Method.POST){ req ->
			uri.path = url
			headers["Authorization"] = authHeader
			headers["Accept"] = "application/zip"
			response.success = { HttpResponseDecorator resp, zip ->
				println "Response status line: ${resp.statusLine}"

				assert resp.statusLine.statusCode == 200, "Failed downloading IPAX generator"
				println "IPAX generator tool successfully received. Deleting ${dirName} before extracting it."
				(new File(dirName)).deleteDir()
				unzip(zip, "", dirName)
			}
		}
		return new File(dirName, "IPAX_Generator.sh")
	}
	
	public def getScan(String scanId, ScanType scanType) {
		String url = getScanUrl(scanId, scanType)
		println "Send GET request to ${this.baseUrl}$url"
		
		def scan = null

		httpBuilder.request(Method.GET, ContentType.JSON){ req ->
			uri.path = url
			headers["Authorization"] = authHeader

			response.success = { HttpResponseDecorator resp ->
				println "Response status line: ${resp.statusLine}"

				scan = parseJsonFromResponseText(resp, "getScan was not retrieved successfully")
				assert resp.statusLine.statusCode == 200 || resp.statusLine.statusCode == 201, "Scan was not retrieved successfully" //201 is only temporary supported
			}
		}
		println "Scan retrieved successfully. Scan name is: ${scan.Name}"
		return scan
	}
	
	public def getAllScans(ScanType scanType) {
		String url = getAllScansUrl(scanType)
		def scans = null
		
		println "Send GET request to ${this.baseUrl}$url"
		httpBuilder.request(Method.GET, ContentType.JSON){ req ->
			uri.path = url
			headers["Authorization"] = authHeader

			response.success = { HttpResponseDecorator resp ->
				println "Response status line: ${resp.statusLine}"

				scans = parseJsonFromResponseText(resp, "getAllScans were not retrieved successfully")
				assert resp.statusLine.statusCode == 200, "User scans were not retrieved successfully"
			}
		}
		println "User scans retrieved successfully"
		return scans
	}
	
	public void deleteScan(String scanId, ScanType scanType) {
		String url = getDeleteScanUrl(scanId, scanType)
		
		println "Send DELETE request to ${this.baseUrl}$url"
		httpBuilder.request(Method.DELETE){ req ->
			uri.path = url
			headers["Authorization"] = authHeader
			
			response.success = { HttpResponseDecorator resp ->
				println "Response status line: ${resp.statusLine}"
				assert resp.statusLine.statusCode == 204, "Failed deleting scan with id: ${scanId}"
				println "scan Id ${scanId} deleted successfully"
			}
		}
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
	}

	protected void bluemixLogin() {
		token = null
		authHeader = null
		apiEnvironment = "BlueMix";
		
		Map<String, Serializable> params = [ Bindingid : username, Password : password]
				
		String url = getBluemixLoginUrl()
		println "Send POST request to ${baseUrl}$url: $params"

		httpBuilder = initializeHttpBuilder(baseUrl)
		try{
			httpBuilder.request(Method.POST, ContentType.JSON){ req ->
				uri.path = url
				requestContentType = ContentType.URLENC
				body = params
	
				response.success = { HttpResponseDecorator resp ->
					println "Response status line: ${resp.statusLine}"
					assert resp.status == 200, "Login failed"
					def json = parseJsonFromResponseText(resp, "Bluemix authentication failed")
					token = json.Token
					println "External Token: ${token}"
				}
				response.failure = { resp ->
					println 'Bluemix authentication failed.'
				}
			}
		}
		catch (Exception e) {
			println "Failed bluemixLogin with exception: ${e.getMessage()}"
		}
	}

	protected void scxLogin() {
		token = null
		authHeader = null
		apiEnvironment = "SCX";
		
		Map<String, Serializable> params = [ Username : username, Password : password]
				
		String url = getScxLoginUrl()
		println "Send POST request to ${baseUrl}$url: $params"

		httpBuilder = initializeHttpBuilder(baseUrl)
		try{
			httpBuilder.request(Method.POST, ContentType.JSON){ req ->
				uri.path = url
				requestContentType = ContentType.URLENC
				body = params
	
				response.success = { HttpResponseDecorator resp ->
					println "Response status line: ${resp.statusLine}"
					assert resp.status == 200, "Login failed"
					def json = parseJsonFromResponseText(resp, "SCX authentication failed")
					token = json.Token
					println "External Token: ${token}"
				}
				response.failure = { resp ->
					println 'SCX authentication failed.'
				}
			}
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
	
	protected HTTPBuilder initializeHttpBuilder(String baseUrl) {
		HTTPBuilder httpBuilder = new HTTPBuilder(baseUrl)
		
		SSLContextBuilder sslContextBuilder = new SSLContextBuilder();
		if (!validateSSL) {
			sslContextBuilder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
		}
		SSLConnectionSocketFactory sslcsf = new SSLConnectionSocketFactory(sslContextBuilder.build())
		CloseableHttpClient httpclient = HttpClients.custom().setSSLSocketFactory(sslcsf).build();
		
		httpBuilder.setClient(httpclient);
		return httpBuilder
	}
	
	protected abstract String getScanUrl(String scanId, ScanType scanType)
	protected abstract def login()
	protected abstract String getBluemixLoginUrl()
	protected abstract String getScxLoginUrl()
	protected abstract String getDeleteScanUrl(String scanId, ScanType scanType)
	protected abstract def getAllScansUrl(ScanType scanType)
	protected abstract String uploadARSA(File arsaFile, String parentjobid)
	public abstract void waitForScan(String scanId, ScanType scanType, Long scanTimeout, Long startTime, String issueCountString)
	public abstract String startDastScan(String startingUrl, String loginUsername, String loginPassword, String parentjobid, String presenceId)
}
