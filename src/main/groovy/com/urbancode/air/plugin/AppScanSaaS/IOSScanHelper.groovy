/**
 * (c) Copyright IBM Corporation 2015.
 * (c) Copyright HCL Technologies Ltd. 2018. All Rights Reserved.
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

/**
 * Helper class that provides functions to create/delete the iOS package file used during
 * a mobile iOS application scan. This class is currently unused, but the logic for creating
 * the IPAX file is here in case the need ever arises.
 */
public class IOSScanHelper {
    public IOSScanHelper() {}

	private int cleanEntriesFromIPAX(String ipaxFileName, String postfixOfEntryToClean){
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

	private File generateIPAX(RestClient restClient, File projectFile, Properties props) {
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