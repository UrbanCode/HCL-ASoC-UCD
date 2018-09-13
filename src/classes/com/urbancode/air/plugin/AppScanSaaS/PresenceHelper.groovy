package com.urbancode.air.plugin.AppScanSaaS

import java.util.concurrent.TimeUnit
import java.util.List
import java.util.Properties

class PresenceHelper {
    private final String serviceWorkingDirectory = "AppscanPresenceService"
    private final String agentServiceJar = "agentService.jar"
    private final String clientJar = "TunnelClient.jar"
    private final String windowsScript = "startPresence.vbs"
    private final String unixScript = "startPresence.sh"

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
            File scriptFile = new File(serviceWorkingDirectory, windowsScript)
            args.add("cscript.exe")
            args.add(scriptFile.getAbsolutePath())
        } else {
            File scriptFile = new File(serviceWorkingDirectory, unixScript)
            scriptFile.setExecutable(true)
            args.add("/bin/sh")
            args.add(scriptFile.getAbsolutePath())
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

        killMatchingProcess(clientJar)
        killMatchingProcess(agentServiceJar)
    }

    private void killMatchingProcess(String jarToMatch) {
        if (isWindows) {
            List<String> args = [
                "wmic",
                "Path",
                "win32_process",
                "Where",
                "CommandLine Like '%-jar%${jarToMatch}%'",
                "Call",
                "Terminate"
            ]
            executeCommand(args)
        }
        else {
            println("[Action] Searching for processes matching ${jarToMatch}.")
            List<String> args = ["pgrep", "-f", jarToMatch]
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
}
