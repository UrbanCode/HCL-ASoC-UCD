/**
 * � Copyright IBM Corporation 2015.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

package com.urbancode.air.plugin.AppScanSaaS

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.urbancode.air.AirPluginTool;
import com.urbancode.air.plugin.AppScanSaaS.RestClient
import com.urbancode.air.plugin.AppScanSaaS.ScanType

public class SastScanHelper {
    public SastScanHelper() {}

	protected File generateARSA(String arsaToolDir, File scanDirectory, String configFile, boolean encrypt) {
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

		def exitVal = process.exitValue()
		if (!exitVal) {
			println "Command ended with exitValue = $exitVal , process.text = ${process.text}"
		} else {
			println "Command failed with exitValue = $exitVal , process.err.text = ${process.err.text}"
		}

		return arsaFile
	}
}