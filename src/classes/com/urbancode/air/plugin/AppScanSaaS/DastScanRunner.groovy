/**
 * © Copyright IBM Corporation 2015.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

package com.urbancode.air.plugin.AppScanSaaS
import java.util.concurrent.TimeUnit;

import com.urbancode.air.plugin.AppScanSaaS.RestClient
import com.urbancode.air.plugin.AppScanSaaS.ScanType


public class DastScanRunner {
	private static String serviceWorkingDirectory = "AppscanPresenceService"
	private static String agentServiceJar = "agentService.jar"
	private static String clientJar = "TunnelClient.jar"
	private static String windowsScript = "startPresence.vbs"
	private static String unixScript = "startPresence.sh"

	public static String runDastScan(Properties props, RestClient restClient) {
		final def validateReport = false;
		String issueCountString = "";
		if (props.containsKey("validateReport")) {
			validateReport = Boolean.valueOf(props['validateReport'])
		} else if (props.containsKey("reportIssueCountValidation")) {
			issueCountString = props['reportIssueCountValidation'];
			validateReport = !issueCountString.isEmpty();
		}

		String startingUrl = props["startingUrl"]
		if (startingUrl == null || startingUrl.isEmpty()){
			println "Missing starting url"
			System.exit 1
		}

		String scanUser = props["scanUser"]
		String scanPassword = props["scanPassword"]
		String parentjobid = props["parentScanId"]

		String presenceId = ""
		if (props.containsKey("presenceId")) {
			presenceId = props["presenceId"]
		}
		
		String testPolicy = ""
		if (props.containsKey("testPolicy")) {
			testPolicy = props["testPolicy"]
		}
		
		String appId = ""
		if (props.containsKey("applicationId")) {
			appId = props["applicationId"]
		}
		
		String scanType = "Production"
		if (props.containsKey("scanType")) {
			scanType = props["scanType"]
		}
		
		String scanId = restClient.startDastScan(startingUrl, scanUser, scanPassword, parentjobid, presenceId, testPolicy, appId, scanType)

		Long startTime = System.currentTimeMillis()
		if (validateReport){
			final def scanTimeout = 45
			try {
				scanTimeout = Integer.parseInt(props['scanTimeout'])
			} catch (NumberFormatException){
			}

			restClient.waitForScan(scanId, ScanType.DAST, TimeUnit.MINUTES.toMillis(scanTimeout), startTime, issueCountString, props)
		}

		return scanId;
	}

	public static String createAndStartPresence(Properties props, RestClient restClient) {
		println "Preparing Private Site Scanning Tunnel Server Setup"

		String newPresenceId = restClient.createNewPresence()

		stopRunningPresence()

		boolean isWindows = isWindows()

		restClient.downloadAppscanPresence(serviceWorkingDirectory, isWindows)
		restClient.downloadPresenceKeyFile(serviceWorkingDirectory, newPresenceId)

		//start java agent service
		List<String> args = new ArrayList<String>()
		if (isWindows) {
			File scriptFile = new File(serviceWorkingDirectory, windowsScript)
			args.add("cscript.exe")
			args.add(scriptFile.getAbsolutePath())
		} else {
			File scriptFile = new File(serviceWorkingDirectory, unixScript)
			scriptFile.setExecutable(true)
			args.add("/bin/sh")
			args.add(scriptFile.getAbsolutePath())
		}

		println "Starting AppScan Presence with command: ${args}"
				
		Process process = args.execute(null, new File(serviceWorkingDirectory))
		if (isWindows) {
			//the windows batch file starts the presence in a new shell window, so we wait for the current shell to finish
			//we give it some sime to extract the Java zip
			waitForProcess(process, (int)TimeUnit.MINUTES.toMillis(10), true)
		}
		
		restClient.verifyPresenceId(newPresenceId)

		return newPresenceId
	}

	public static void cleanUpPrivateScan() {
		stopRunningPresence()
	}

	private static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().contains("win");
	}

	private static void stopRunningPresence() {
		println "Killing existing service"

		//kill existing service just in case
		if (isWindows()) {
			List<String> args = [
				"wmic",
				"Path",
				"win32_process",
				"Where",
				"CommandLine Like '%-jar%${clientJar}%'",
				"Call",
				"Terminate"
			]
			executeCommand(args)

			args = [
				"wmic",
				"Path",
				"win32_process",
				"Where",
				"CommandLine Like '%-jar%${agentServiceJar}%'",
				"Call",
				"Terminate"
			]
			executeCommand(args)

		} else {
			String out = executeCommand("pgrep -f ${clientJar}")
			out.eachLine {
				String pid = it
				executeCommand("kill -s 15 ${pid}")
			}

			out = executeCommand("pgrep -f ${agentServiceJar}")
			out.eachLine {
				String pid = it
				executeCommand("kill -s 15 ${pid}")
			}
		}
	}

	private static String executeCommand(String command) {
		println "Running command: '${command}'"

		Process proc = command.execute()
		return waitForProcess(proc, (int)TimeUnit.SECONDS.toMillis(5), true)
	}

	private static String executeCommand(List args) {
		println "Running command: '${args}'"

		Process proc = args.execute()
		return waitForProcess(proc, (int)TimeUnit.SECONDS.toMillis(5), true)
	}

	private static String waitForProcess(Process proc, int waitTime, boolean getExitCode) {
		StringBuffer sout = new StringBuffer()
		StringBuffer serr = new StringBuffer()
		proc.consumeProcessOutput(sout, serr)
		proc.waitForOrKill(waitTime)
		println "Command Output: $sout"
		println "Command Error:  $serr"

		if (getExitCode) {
			try  {
				int exitCode = proc.exitValue()
				println "Command Exit Code:  $exitCode"
			} catch (Exception e){
				println "Failed to get Command Exit Code Failed: " + e.getMessage()
			}
		}

		return sout.toString()
	}
}