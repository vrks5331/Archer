package com.archer.actions;

import java.awt.Desktop;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class ActionExecutor {

    public static String execute(String action, String target, String value) {
        try {
            action = (action != null) ? action.toLowerCase(Locale.ROOT) : "";
            switch (action) {
                case "open_app":
                    return openApp(target);
                case "search_web":
                    return searchWeb(target);
                case "set_brightness":
                    return setBrightness(value);
                case "check_battery":
                    return checkBattery();
                case "run_command":
                    return runCommand(target);
                case "toggle_bluetooth":
                    return toggleBluetooth(value);
                case "toggle_wifi":
                    return toggleWiFi(value);
                default:
                    return "I’m not yet trained to handle that system command.";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error executing command: " + e.getMessage();
        }
    }

    private static String openApp(String app) throws IOException {
        if (app == null) return "No application specified.";
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            new ProcessBuilder("cmd", "/c", "start", app).start();
        } else if (os.contains("mac")) {
            new ProcessBuilder("/usr/bin/open", "-a", app).start();
        } else {
            // Run via shell to allow arbitrary commands/paths
            new ProcessBuilder("/bin/sh", "-c", app).start();
        }
        return "Opened " + app;
    }

    private static String searchWeb(String query) throws Exception {
        if (query == null) return "No search term specified.";
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
        String url = "https://www.google.com/search?q=" + encoded;
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(new URI(url));
            return "Searching for " + query;
        } else {
            // fallback: open via platform-specific command
            String os = System.getProperty("os.name").toLowerCase();
            try {
                if (os.contains("win")) {
                    new ProcessBuilder("cmd", "/c", "start", "", url).start();
                } else if (os.contains("mac")) {
                    new ProcessBuilder("/usr/bin/open", url).start();
                } else {
                    new ProcessBuilder("/bin/sh", "-c", "xdg-open '" + url + "'").start();
                }
                return "Searching for " + query;
            } catch (IOException ioe) {
                throw ioe;
            }
        }
    }

    private static String setBrightness(String value) throws IOException {
        if (value == null) return "No brightness value given.";
        String os = System.getProperty("os.name").toLowerCase();
        double pct;
        try {
            pct = Double.parseDouble(value);
        } catch (NumberFormatException nfe) {
            return "Invalid brightness value: " + value;
        }
        double normalized = pct / 100.0;
        try {
            if (os.contains("win")) {
                // Use CIM instead of deprecated WMI cmdlets
                String script = "Get-CimInstance -Namespace root/WMI -ClassName WmiMonitorBrightnessMethods | Invoke-CimMethod -MethodName WmiSetBrightness -Arguments @{Brightness=" + (int)pct + ";Timeout=1}";
                execCommand(new String[]{"powershell", "-NoProfile", "-Command", script});
            } else if (os.contains("linux")) {
                // detect an active connected output and apply brightness with xrandr
                String sh = "xrandr --listmonitors | awk '/Monitors:/{next} {print $4; exit}' | xargs -I{} xrandr --output {} --brightness " + normalized;
                execCommand(new String[]{"/bin/sh", "-c", sh});
            } else if (os.contains("mac")) {
                execCommand(new String[]{"/bin/sh", "-c", "brightness " + normalized});
            }
        } catch (Exception e) {
            return "Failed to set brightness: " + e.getMessage();
        }
        return "Set brightness to " + value + "%";
    }

    private static String checkBattery() throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                String script = "Get-CimInstance -ClassName Win32_Battery | Select-Object -ExpandProperty EstimatedChargeRemaining";
                String out = execCommand(new String[]{"powershell", "-NoProfile", "-Command", script});
                return "Battery percentage: " + out.trim();
            } else {
                // run in shell to allow command substitution
                String cmd = "upower -i $(upower -e | grep -m1 BAT) | grep -i percentage | awk '{print $2}'";
                String out = execCommand(new String[]{"/bin/sh", "-c", cmd});
                return "Battery percentage: " + out.trim();
            }
        } catch (Exception e) {
            return "Battery info retrieval failed: " + e.getMessage();
        }
    }

    private static String runCommand(String cmd) throws IOException {
        if (cmd == null) return "No command specified.";
        String os = System.getProperty("os.name").toLowerCase();
        try {
            String out;
            if (os.contains("win")) {
                out = execCommand(new String[]{"cmd", "/c", cmd});
            } else {
                out = execCommand(new String[]{"/bin/sh", "-c", cmd});
            }
            return "Command output: " + out;
        } catch (Exception e) {
            return "Command failed: " + e.getMessage();
        }
    }

    private static String toggleBluetooth(String state) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                String script;
                if (state != null && state.equalsIgnoreCase("on")) {
                    script = "Start-Service -Name bthserv -ErrorAction SilentlyContinue";
                } else if (state != null && state.equalsIgnoreCase("off")) {
                    script = "Stop-Service -Name bthserv -Force -ErrorAction SilentlyContinue";
                } else {
                    script = "Get-Service -Name bthserv | Select-Object -ExpandProperty Status";
                }
                String out = execCommand(new String[]{"powershell", "-NoProfile", "-Command", script});
                return "Bluetooth: " + out.trim();
            } else if (os.contains("linux")) {
                // Many distros require rfkill or bluetoothctl; attempt rfkill if available
                String cmd = (state != null && state.equalsIgnoreCase("off")) ? "rfkill block bluetooth" : "rfkill unblock bluetooth";
                String out = execCommand(new String[]{"/bin/sh", "-c", cmd});
                return "Bluetooth: " + (out.isEmpty() ? "toggled " + state : out);
            } else {
                String cmd = (state != null && state.equalsIgnoreCase("off")) ? "sudo defaults write /Library/Preferences/com.apple.Bluetooth.plist ControllerPowerState -int 0; sudo killall -HUP blued" : "sudo defaults write /Library/Preferences/com.apple.Bluetooth.plist ControllerPowerState -int 1; sudo killall -HUP blued";
                String out = execCommand(new String[]{"/bin/sh", "-c", cmd});
                return "Bluetooth: " + (out.isEmpty() ? "toggled " + state : out);
            }
        } catch (Exception e) {
            return "Bluetooth toggle failed: " + e.getMessage();
        }
    }

    private static String toggleWiFi(String state) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                String script = (state != null && state.equalsIgnoreCase("on")) ? "Enable-NetAdapter -Name 'Wi-Fi' -Confirm:$false" : "Disable-NetAdapter -Name 'Wi-Fi' -Confirm:$false";
                // Try using PowerShell NetAdapter cmdlets; fall back to netsh if cmdlet missing
                String out = execCommand(new String[]{"powershell", "-NoProfile", "-Command", script + "; if ($?) { 'ok' } else { (netsh interface set interface \"Wi-Fi\" admin=" + (state.equalsIgnoreCase("on") ? "enabled" : "disabled") + ") }"});
                return "Wi-Fi: " + out.trim();
            } else if (os.contains("linux")) {
                String cmd = "nmcli radio wifi " + (state != null && state.equalsIgnoreCase("on") ? "on" : "off");
                String out = execCommand(new String[]{"/bin/sh", "-c", cmd});
                return "Wi-Fi: " + out.trim();
            } else {
                String cmd = "networksetup -setairportpower en0 " + (state != null && state.equalsIgnoreCase("on") ? "on" : "off");
                String out = execCommand(new String[]{"/bin/sh", "-c", cmd});
                return "Wi-Fi: " + out.trim();
            }
        } catch (Exception e) {
            return "Wi-Fi toggle failed: " + e.getMessage();
        }
    }

    // Helper that runs a command and returns its stdout (combined with stderr). Swallows interruption and returns any output collected.
    private static String execCommand(String[] command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        try {
            p.waitFor();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        return output.toString().trim();
    }
}
