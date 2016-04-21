/**
 * © Copyright IBM Corporation 2015.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

package com.urbancode.air.plugin.AppScanSaaS

import java.util.concurrent.TimeUnit;

import com.urbancode.air.AirPluginTool;
import com.urbancode.air.plugin.AppScanSaaS.RestClientBase
import com.urbancode.air.plugin.AppScanSaaS.ScanType

public class SastScanRunner {
	public static String runSastScan(Properties props, RestClientBase restClient) {
		final def validateReport = false;
		
		String issueCountString = "";
		if (props.containsKey("validateReport")) {
			validateReport = Boolean.valueOf(props['validateReport'])
		} else if (props.containsKey("reportIssueCountValidation")) {
			issueCountString = props['reportIssueCountValidation'];
			validateReport = !issueCountString.isEmpty();
		}
		
		String sastFileLocation = props['sastFileLocation']
		
		if (!sastFileLocation) {
			println "sastFileLocation has not been specified."
			System.exit 1
		}
		
		File arsaFile = new File(sastFileLocation)
		if (!arsaFile.exists()){
			println "SAST file $arsaFile doesn't exist."
			System.exit 1
		}
		
		String parentjobid = props["parentScanId"]
		
		assert restClient != null, "Invalid plugin configuration"
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
			
			arsaFile = generateARSA(arsaToolDir, arsaFile, props["sastConfigFile"], encrypt)
			assert arsaFile.exists(), 'IRX file generation failed.'
		}
		
		String scanId = restClient.uploadARSA(arsaFile, parentjobid)
		
		if (isGenerateARSA) {
			arsaFile.delete()
		}
		
		Long startTime = System.currentTimeMillis()
		if (validateReport){
			final def scanTimeout = 45
			try {
				scanTimeout = Integer.parseInt(props['scanTimeout'])
				
			} catch (NumberFormatException){}
			
			restClient.waitForScan(scanId, ScanType.SAST, TimeUnit.MINUTES.toMillis(scanTimeout), startTime, issueCountString)
		}
		
		return scanId
	}
	
	protected static File generateARSA(String arsaToolDir, File scanDirectory, String configFile, boolean encrypt) {
		boolean isWindows = (System.getProperty('os.name') =~ /(?i)windows/).find()
		File arsaToolBin = new File(arsaToolDir, 'bin')
		String arsaExeName = isWindows? 'appscan.bat': 'appscan.sh'
		File arsaExe = new File(arsaToolBin, arsaExeName)
		assert arsaExe.exists(), "$arsaExe not found.  IRX file cannot be generated."
		String arsaName = scanDirectory.getName()
		File arsaFile = new File(scanDirectory, arsaName + '.irx')
		
		if (arsaFile.exists()) {
			arsaFile.delete()
		}
		
		def command = [arsaExe.absolutePath, 'prepare', '-n', arsaName]
		
		if (configFile) {
			command.addAll(['-c', configFile])
			println "Using configuration file $configFile"
		}
		
		if (!encrypt) {
			command.add('-ne')
		}
			
		println "Running the following command arguments: $command"
		def process = command.execute(System.getenv().collect { k, v -> "$k=$v" }, scanDirectory)
		process.waitFor()
		
		if (process.exitValue()) {
			println process.text
		} else {
			println process.err.text
		}
		
		return arsaFile
	}
}