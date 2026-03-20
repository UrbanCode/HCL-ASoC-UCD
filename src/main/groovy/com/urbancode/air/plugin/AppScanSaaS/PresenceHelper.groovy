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

        println("[DEBUG] Starting Presence for ID: " + presenceId);
        println("[DEBUG] Working Directory: " + serviceWorkingDirectory);

        restClient.downloadAppscanPresence(serviceWorkingDirectory, isWindows, presenceId);
        println("[DEBUG] Presence package downloaded.");

        File dir = new File(serviceWorkingDirectory);

        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().toLowerCase().contains("key")) {
                    println("[DEBUG] Deleting old key file: " + f.getAbsolutePath());
                    f.delete();
                }
            }
        }

        if (renewKey) {
            println("[DEBUG] Renewing Presence key...");
            restClient.renewPresenceKeyFile(serviceWorkingDirectory, presenceId);
            println("[DEBUG] Presence key renewed.");
        }

        File newKey = new File(serviceWorkingDirectory, "AppScanPresence.key");
        File expectedKey = new File(serviceWorkingDirectory, "Presence.key");

        if (newKey.exists()) {
            println("[DEBUG] Renaming key file to Presence.key");
            newKey.renameTo(expectedKey);
        }

        if (!expectedKey.exists() || expectedKey.length() == 0) {
            throw new RuntimeException("Valid Presence.key file not found!");
        }

        println("[DEBUG] Key file ready: " + expectedKey.getAbsolutePath());

        println("[DEBUG] Stopping any running Presence...");
        stopRunningPresence();

        List<String> args = new ArrayList<>();
        boolean launchedDirectProcess = false;

        if (isWindows) {
            File windowsPresenceExecutable = new File(serviceWorkingDirectory, windowsPresenceExe);

            if (!windowsPresenceExecutable.exists()) {
                throw new RuntimeException("Presence.exe not found in " + serviceWorkingDirectory);
            }

            args.add(windowsPresenceExecutable.getAbsolutePath());
            launchedDirectProcess = true;

            println("[DEBUG] Using Presence.exe directly: " + windowsPresenceExecutable.getAbsolutePath());

        } else {
            File linuxPresenceExecutable = new File(serviceWorkingDirectory, linuxPresenceBinary);

            if (!linuxPresenceExecutable.exists()) {
                throw new RuntimeException("Presence binary not found in " + serviceWorkingDirectory);
            }

            linuxPresenceExecutable.setExecutable(true);
            args.add(linuxPresenceExecutable.getAbsolutePath());

            println("[DEBUG] Using Linux binary: " + linuxPresenceExecutable.getAbsolutePath());
        }

        println("[ACTION] Starting AppScan Presence with command: " + args);

        try {
            Process process = args.execute(null, new File(serviceWorkingDirectory));

            println("[DEBUG] Process started. Capturing output...");

            Thread.startDaemon {
                process.inputStream.eachLine { line ->
                    println("[PRESENCE-OUT] " + line);
                }
            }

            Thread.startDaemon {
                process.errorStream.eachLine { line ->
                    println("[PRESENCE-ERR] " + line);
                }
            }

            Thread.sleep(10000);

        } catch (Exception e) {
            println("[ERROR] Failed to start Presence: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        println("[DEBUG] Verifying Presence ID with server...");
        restClient.verifyPresenceId(presenceId);

        println("[SUCCESS] Presence started successfully.");
        return presenceId;
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
        if (waitForSpawnOnly) {
            StringBuffer sout = new StringBuffer()
            StringBuffer serr = new StringBuffer()
            long sleepTime = Math.min(waitTime, (int)TimeUnit.SECONDS.toMillis(5))
            sleep(sleepTime)
            proc.consumeProcessOutput(sout, serr)

            return sout.toString()
        }

        return waitForProcess(proc, waitTime)
    }
}
