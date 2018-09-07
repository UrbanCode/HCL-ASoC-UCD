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
        println "Preparing Private Site Scanning Tunnel Server Setup."

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

        println "Starting AppScan Presence with command: ${args}"

        Process process = args.execute(null, new File(serviceWorkingDirectory))
        if (isWindows) {
            /* The windows batch file starts the presence in a new shell window, so we wait for
             * the current shell to finish. We give it some time to extract the Java zip. */
            waitForProcess(process, (int)TimeUnit.MINUTES.toMillis(10), true)
        }

        restClient.verifyPresenceId(presenceId)
    }

    private void stopRunningPresence() {
        println "Killing existing service..."

        //kill existing service just in case
        if (isWindows) {
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

    private String executeCommand(String command) {
        println "Running command: '${command}'"

        Process proc = command.execute()
        return waitForProcess(proc, (int)TimeUnit.SECONDS.toMillis(5), true)
    }

    private String executeCommand(List args) {
        println "Running command: '${args}'"

        Process proc = args.execute()
        return waitForProcess(proc, (int)TimeUnit.SECONDS.toMillis(5), true)
    }

    private String waitForProcess(Process proc, int waitTime, boolean getExitCode) {
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
