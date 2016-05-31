/**
 * © Copyright IBM Corporation 2015.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

package com.urbancode.air.plugin.AppScanSaaS

import java.util.concurrent.TimeUnit;

import groovy.io.FileType
import java.lang.Process
import java.util.Properties
import com.urbancode.air.plugin.AppScanSaaS.RestClientBase
import com.urbancode.air.plugin.AppScanSaaS.ScanType

public class iOSScanRunner {
	public static String runIOSScan(Properties props, RestClientBase restClient) {
		final def validateReport = false;
		String issueCountString = "";
		if (props.containsKey("validateReport")) {
			validateReport = Boolean.valueOf(props['validateReport'])
		} else if (props.containsKey("reportIssueCountValidation")) {
			issueCountString = props['reportIssueCountValidation'];
			validateReport = !issueCountString.isEmpty();
		}

		File projectFile = new File(props["projectLocation"])
		if (!projectFile.exists()){
			println "Project/Workspace doesn't exist"
			System.exit 1
		}

		File ipaxFile = generateIPAX(restClient, projectFile, props);

		String appUsername = props["appUsername"]
		String appPassword = props["appPassword"]
		String parentjobid = props["parentScanId"]

		String scanId = restClient.uploadIPAX(ipaxFile, appUsername, appPassword, parentjobid)


		Long startTime = System.currentTimeMillis()
		if (validateReport){
			final def scanTimeout = 45
			try {
				scanTimeout = Integer.parseInt(props['scanTimeout'])
			} catch (NumberFormatException){

			}

			restClient.waitForScan(scanId, ScanType.IOS, TimeUnit.MINUTES.toMillis(scanTimeout), startTime, issueCountString)
		}

		return scanId;
	}
	
	private static File generateIPAX(RestClientBase restClient, File projectFile, Properties props) {
		File ipaxBash = restClient.getIPAXGenerator()
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
		
		//create config.txt for IPAX tool
		Properties ipaxProps = new Properties()
		ipaxProps.putAt("siteUrl", restClient.getBaseUrl())
		//ipaxProps.putAt("byPassSSL", "true")
		File ipaxPropsFile = new File(ipaxBash.getParentFile(), "config.txt")
        OutputStream out = new FileOutputStream( ipaxPropsFile )
        ipaxProps.store(out, "")
		
		String userName = props["loginUsername"]
		String outputDirName = "IPAX_Generator_Output_for_${userName}"
		File outputDir = new File(outputDirName)
		outputDir.deleteDir()
		args.add("-outputPath")
		args.add(outputDir.getAbsolutePath())
		
		args.add("-silent")
		args.add("-includeLogs")
		
		ProcessBuilder processBuilder = new ProcessBuilder(args)
		processBuilder.directory(ipaxBash.getParentFile())
		Process process = processBuilder.start();
		process.waitForProcessOutput(System.out, System.err)
		
		def found = []
		outputDir.eachFileMatch(FileType.FILES, ~/.*\.ipax/) {
			found << it.name
		}
		
		assert found.size() == 1, "Failed creating IPAX file"
		println "Uploading IPAX File: " + outputDir + "/" + found[0]
		return new File(outputDir, found[0])
	}
}