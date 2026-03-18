/**
 * (c) Copyright HCL Technologies Ltd. 2018. All Rights Reserved.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 */

package com.urbancode.air.plugin.AppScanSaaS

import java.util.concurrent.TimeUnit
import java.util.List
import java.util.Properties

/**
 * Helper class that provides all of the functions for running shell (Linux/Mac) or console (Windows)
 * commands to start and stop presences on the agent machine.
 */
class PresenceHelper {
    private final String serviceWorkingDirectory = "AppscanPresenceService"
    private final String agentServiceJar = "agentService.jar"
    private final String clientJar = "TunnelClient.jar"
    private final String windowsScript = "startPresence.vbs"
    private final String windowsServiceScript = "StartPresenceAsService.bat"
    private final String windowsPresenceExe = "Presence.exe"
    private final String windowsPresenceUpdaterExe = "PresenceUpdater.exe"
    private final String unixScript = "startPresence.sh"
    private final String linuxPresenceBinary = "Presence"

    private RestClient restClient
    private boolean isWindows

    public PresenceHelper(RestClient restClient, boolean isWindows) {
        this.restClient = restClient
        this.isWindows = isWindows
    }

    public String createPresence(boolean start) {
        println("[Action] Preparing Private Site Scanning Tunnel Server Setup.")

        String newPresenceId = restClient.createNewPresence()

        if (start) {
            startPresence(newPresenceId, true)  // Create presence and generate new key
        }

        return newPresenceId
    }

    public void stopPresence(String presenceId) {
        restClient.downloadAppscanPresence(serviceWorkingDirectory, isWindows, presenceId)
        stopRunningPresence()
    }

    private String startPresence(String presenceId, boolean renewKey) {
        restClient.downloadAppscanPresence(serviceWorkingDirectory, isWindows, presenceId)

        if (renewKey) {
            restClient.renewPresenceKeyFile(serviceWorkingDirectory, presenceId)
        }

        stopRunningPresence()

        /* Start java agent service */
        List<String> args = new ArrayList<String>()
        if (isWindows) {
            File serviceScriptFile = new File(serviceWorkingDirectory, windowsServiceScript)
            File legacyScriptFile = new File(serviceWorkingDirectory, windowsScript)
            File windowsPresenceExecutable = new File(serviceWorkingDirectory, windowsPresenceExe)

            if (serviceScriptFile.exists()) {
                args.add("cmd.exe")
                args.add("/c")
                args.add(serviceScriptFile.getAbsolutePath())
            }
            else if (legacyScriptFile.exists()) {
                args.add("cscript.exe")
                args.add(legacyScriptFile.getAbsolutePath())
            }
            else if (windowsPresenceExecutable.exists()) {
                args.add(windowsPresenceExecutable.getAbsolutePath())
            }
            else {
                assert false, "Presence launcher not found in ${serviceWorkingDirectory}. Expected one of ${windowsServiceScript}, ${windowsScript}, ${windowsPresenceExe}."
            }
        } else {
            File scriptFile = new File(serviceWorkingDirectory, unixScript)
            File linuxPresenceExecutable = new File(serviceWorkingDirectory, linuxPresenceBinary)
            if (scriptFile.exists()) {
                scriptFile.setExecutable(true)
                args.add("/bin/sh")
                args.add(scriptFile.getAbsolutePath())
            }
            else if (linuxPresenceExecutable.exists()) {
                linuxPresenceExecutable.setExecutable(true)
                args.add(linuxPresenceExecutable.getAbsolutePath())
            }
            else {
                assert false, "Presence launcher not found in ${serviceWorkingDirectory}. Expected one of ${unixScript}, ${linuxPresenceBinary}."
            }
        }

        println("[Action] Starting AppScan Presence with command: ${args}.")

        Process process = args.execute(null, new File(serviceWorkingDirectory))
        if (isWindows) {
            /* The windows batch file starts the presence in a new shell window, so we wait for
             * the current shell to finish. We give it some time to extract the Java zip. */
            waitForProcess(process, (int)TimeUnit.MINUTES.toMillis(10), true)
        }

        restClient.verifyPresenceId(presenceId)
    }

    private void stopRunningPresence() {
        println("[Action] Killing existing service...")

        if (isWindows) {
            killMatchingProcess(windowsPresenceExe)
            killMatchingProcess(windowsPresenceUpdaterExe)
        }

        killMatchingProcess(clientJar)
        killMatchingProcess(agentServiceJar)
    }

    private void killMatchingProcess(String processToMatch) {
        if (isWindows) {
            List<String> args
            if (processToMatch.toLowerCase().endsWith(".jar")) {
                args = [
                    "wmic",
                    "Path",
                    "win32_process",
                    "Where",
                    "CommandLine Like '%-jar%${processToMatch}%'",
                    "Call",
                    "Terminate"
                ]
            }
            else {
                args = ["taskkill", "/F", "/IM", processToMatch]
            }
            executeCommand(args)
        }
        else {
            println("[Action] Searching for processes matching ${processToMatch}.")
            List<String> args = ["pgrep", "-f", processToMatch]
            String out = executeCommand(args)

            out.eachLine {
                String pid = it
                println("[OK] Found matching PID to kill: ${pid}")
                args = ["kill", "-s", "15", pid]
                executeCommand(args)
            }
        }
    }

    private String executeCommand(List args) {
        println("[Action] Running command: '${args.join(' ')}'.")

        Process proc = args.execute()
        return waitForProcess(proc, (int)TimeUnit.SECONDS.toMillis(5))
    }

    private String waitForProcess(Process proc, int waitTime) {
        StringBuffer sout = new StringBuffer()
        StringBuffer serr = new StringBuffer()
        proc.waitForOrKill(waitTime)
        proc.consumeProcessOutput(sout, serr)

        return sout.toString()
    }

    private String waitForProcess(Process proc, int waitTime, boolean waitForSpawnOnly) {
        return waitForProcess(proc, waitTime)
    }
}
