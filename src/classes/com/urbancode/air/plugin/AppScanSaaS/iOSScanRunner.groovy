/**
 * � Copyright IBM Corporation 2015.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

package com.urbancode.air.plugin.AppScanSaaS

import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

import groovy.io.FileType

import java.lang.Process
import java.util.Properties

import com.urbancode.air.plugin.AppScanSaaS.RestClient
import com.urbancode.air.plugin.AppScanSaaS.ScanType

public class iOSScanRunner {
	public static String runIOSScan(Properties props, RestClient restClient) {
		final def validateReport = false;
		String issueCountString = "";
		if (props.containsKey("validateReport")) {
			validateReport = Boolean.valueOf(props['validateReport'])
		} else if (props.containsKey("reportIssueCountValidation")) {
			issueCountString = props['reportIssueCountValidation'];
			validateReport = !issueCountString.isEmpty();
		}

		File scanFile = null;

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
				println "Not enough input was provided for this step. Please add a value to the required 'IPA file location' field ('ipaFileLocation')"
				System.exit 1
			}
			File projectFile = new File(projectLocation)
			if (!projectFile.exists()){
				println "Project/Workspace doesn't exist"
				System.exit 1
			}

			scanFile = generateIPAX(restClient, projectFile, props);
		}

		String appUsername = props["appUsername"]
		String appPassword = props["appPassword"]
        String thirdCredential = props['thirdCredential']
		String parentjobid = props["parentScanId"]
		String appId = props["applicationId"]

		String scanId = restClient.startMobileScan(
            ScanType.IOS,
            scanFile,
            appUsername,
            appPassword,
            thirdCredential,
            parentjobid,
            appId)

		Long startTime = System.currentTimeMillis()
		if (validateReport){
			final def scanTimeout = 45
			try {
				scanTimeout = Integer.parseInt(props['scanTimeout'])
			} catch (NumberFormatException){

			}

			restClient.waitForScan(scanId, ScanType.IOS, TimeUnit.MINUTES.toMillis(scanTimeout), startTime, issueCountString, props)
		}

		return scanId;
	}

	private static int cleanEntriesFromIPAX(String ipaxFileName, String postfixOfEntryToClean){
		assert ipaxFileName.endsWith(".ipax")
		assert ipaxFileName.indexOf(".ipax") == ipaxFileName.length() - 5 // 5 is the length of ".ipax"

		String zipFileName = ipaxFileName.replaceFirst(".ipax",".zip")
		new File(ipaxFileName).renameTo(new File(zipFileName))
		ZipFile zipFile = new ZipFile(zipFileName)

		String tmpZipFileName = ipaxFileName.replaceFirst(".ipax","_temp_${System.nanoTime()}.zip")
		File tmpFile = new File(tmpZipFileName)

		int numberOfDeletions = 0
		tmpFile.withOutputStream { outputStream ->
			ZipOutputStream zipOS = new ZipOutputStream(outputStream)
			zipFile.entries().each { entry ->
				if (!entry.name.endsWith(postfixOfEntryToClean)) {
					zipOS.putNextEntry(entry)
					zipOS << (zipFile.getInputStream(entry).bytes)
					zipOS.closeEntry()
				}
				else {
					println "Deleting ${entry.name} from IPAX"
					numberOfDeletions++
				}
			}
			zipOS.close()
		}
		zipFile.close()
		assert new File(zipFileName).delete()
		tmpFile.renameTo(ipaxFileName)
		return numberOfDeletions
	}

	private static File generateIPAX(RestClient restClient, File projectFile, Properties props) {
		String userName = props["loginUsername"]
		String toolDirName = "IPAX_Generator_for_${userName}"
		String ipaxOutputDirName = "IPAX_Generator_Output_for_${userName}"
		String suffixToDeleteFromIPAX = props['suffixToDeleteFromIPAX'];
		if (suffixToDeleteFromIPAX != null && suffixToDeleteFromIPAX.length() > 0) {
			toolDirName = toolDirName + "_suffixToDeleteIs_" + suffixToDeleteFromIPAX
			ipaxOutputDirName = ipaxOutputDirName + "_suffixToDeleteIs_" + suffixToDeleteFromIPAX
		}
		File ipaxBash = restClient.getIPAXGenerator(toolDirName)

		ipaxBash.setExecutable(true)
		List<String> args = new ArrayList<String>()
		args.add(ipaxBash.getAbsolutePath())
		if (projectFile.getAbsolutePath().toLowerCase().endsWith(".xcodeproj")) {
			args.add("-project")
			args.add(projectFile.getAbsolutePath())
		} else {
			args.add("-workspace")
			args.add(projectFile.getAbsolutePath())
			args.add("-scheme")
			args.add(props['workspaceScheme'])
		}

		File outputDir = new File(ipaxOutputDirName)
		outputDir.deleteDir()
		args.add("-outputPath")
		args.add(outputDir.getAbsolutePath())
		args.add("-includeLogs")

		Boolean isSilent = true;
		String silentIPAX = props['silentIPAX'];
		if (silentIPAX != null && silentIPAX.equalsIgnoreCase("false")) {
			isSilent = false;
		}

		if (isSilent){
			args.add("-silent")
		}

		ProcessBuilder processBuilder = new ProcessBuilder(args)
		processBuilder.directory(ipaxBash.getParentFile())

		Process process = processBuilder.start();

		if (isSilent){
			process.waitForProcessOutput(System.out, System.err)
		}else{
			StringBuffer sout = new StringBuffer()
			StringBuffer serr = new StringBuffer()
			process.consumeProcessOutput(sout, serr)
			process.waitForOrKill(TimeUnit.MINUTES.toMillis(5))

			println "Command stdout: $sout"
			println "Command stderr: $serr"
			try  {
				int exitCode = process.exitValue()
				println "Command Exit Code:  $exitCode"
			} catch (Exception e){
				println "Failed to get Command Exit Code Failed: " + e.getMessage()
			}
		}

		def found = []
		outputDir.eachFileMatch(FileType.FILES, ~/.*\.ipax/) {
			found << it.name
		}
		assert found.size() == 1, "Failed creating IPAX file"

		String generatedIpaxFileName = ipaxOutputDirName + "/" + found[0]

		if (suffixToDeleteFromIPAX != null && suffixToDeleteFromIPAX.length() > 0) {
			if (cleanEntriesFromIPAX(generatedIpaxFileName, suffixToDeleteFromIPAX) <= 0){
				println "No file was deleted from IPAX (did not find files with suffix $suffixToDeleteFromIPAX"
			}
		}

		println "Uploading IPAX File: " + generatedIpaxFileName
		return new File(outputDir, found[0])
	}
}