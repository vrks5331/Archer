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
                case "get_system_info":
                    return getSystemInfo(target);
                case "adjust_volume":
                    // value is the percentage, target is the direction (up/down/set)
                    return adjustVolume(value, target);
                case "media_control":
                    return mediaControl(target);
                case "take_screenshot":
                    return takeScreenshot();
                case "open_file":
                    return openFile(target);
                case "create_file":
                    return createFile(target, value);
                case "delete_file":
                    return deleteFile(target);
                case "list_directory":
                    return listDirectory(target);
                case "check_process":
                    return checkProcess(target);
                case "kill_process":
                    return killProcess(target);
                case "shutdown":
                    return shutdown(value);
                default:
                    // Fallback: try to execute as a system command
                    // This allows ANY system command to be executed
                    if (target != null && !target.isEmpty()) {
                        return runCommand(target);
                    }
                    return "I'm not yet trained to handle that system command. Please try rephrasing as a direct command.";
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
            // Normalize app name for Windows
            String normalizedApp = app.toLowerCase().trim();
            
            // Map common app names to their executable names
            String appCommand = getWindowsAppCommand(normalizedApp);
            
            try {
                // Use PowerShell to find and launch the app properly
                // This handles both traditional .exe apps and UWP apps
                String psScript = String.format(
                    "$appName = '%s'; " +
                    "$exeName = '%s'; " +
                    "$found = $false; " +
                    // Method 1: Try Get-Command to find executable in PATH
                    "try { " +
                    "  $cmd = Get-Command $exeName -ErrorAction SilentlyContinue; " +
                    "  if ($cmd) { Start-Process $cmd.Name; $found = $true; exit 0 } " +
                    "} catch {}; " +
                    // Method 2: Try Start Menu shortcuts using Shell.Application
                    "if (-not $found) { " +
                    "  try { " +
                    "    $shell = New-Object -ComObject Shell.Application; " +
                    "    $startMenu = $shell.NameSpace('shell:Common Start Menu'); " +
                    "    $programs = $startMenu.ParseName('Programs'); " +
                    "    if ($programs) { " +
                    "      $items = $programs.GetFolder.Items() | Where-Object { $_.Name -like \"*$appName*\" -or $_.Name -like \"*$exeName*\" }; " +
                    "      if ($items.Count -gt 0) { $items[0].InvokeVerb('open'); $found = $true; exit 0 } " +
                    "    } " +
                    "  } catch {} " +
                    "}; " +
                    // Method 3: Try UWP apps folder
                    "if (-not $found) { " +
                    "  try { " +
                    "    $shell = New-Object -ComObject Shell.Application; " +
                    "    $appsFolder = $shell.NameSpace('shell:AppsFolder'); " +
                    "    $items = $appsFolder.Items() | Where-Object { $_.Name -like \"*$appName*\" -or $_.Path -like \"*$appName*\" }; " +
                    "    if ($items.Count -gt 0) { $items[0].InvokeVerb('open'); $found = $true; exit 0 } " +
                    "  } catch {} " +
                    "}; " +
                    // Method 4: Try direct start with executable name (for well-known apps)
                    "if (-not $found) { " +
                    "  try { Start-Process $exeName -ErrorAction SilentlyContinue; $found = $true } catch {} " +
                    "}; " +
                    // Method 5: Try searching in Program Files
                    "if (-not $found) { " +
                    "  $searchPaths = @('C:\\Program Files', 'C:\\Program Files (x86)', '$env:LOCALAPPDATA\\Programs'); " +
                    "  foreach ($path in $searchPaths) { " +
                    "    if (Test-Path $path) { " +
                    "      $exe = Get-ChildItem -Path $path -Filter \"$exeName.exe\" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1; " +
                    "      if ($exe) { Start-Process $exe.FullName; $found = $true; break } " +
                    "    } " +
                    "  } " +
                    "}; " +
                    "if (-not $found) { Write-Host 'App not found: ' + $appName; exit 1 }",
                    normalizedApp, appCommand
                );
                
                ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", psScript);
                pb.start(); // Don't wait for the process, let it run in background
                return "Opened " + app;
            } catch (Exception e) {
                // Final fallback: Try using start command (may open terminal but better than nothing)
                try {
                    new ProcessBuilder("cmd", "/c", "start", "\"\"", appCommand).start();
                    return "Opened " + app + " (using fallback method)";
                } catch (Exception e2) {
                    return "Failed to open " + app + ": " + e.getMessage();
                }
            }
        } else if (os.contains("mac")) {
            new ProcessBuilder("/usr/bin/open", "-a", app).start();
            return "Opened " + app;
        } else {
            // Linux: Try common locations or use xdg-open
            try {
                new ProcessBuilder("/bin/sh", "-c", "which " + app + " && " + app + " &").start();
            } catch (Exception e) {
                new ProcessBuilder("/bin/sh", "-c", "xdg-open " + app).start();
            }
            return "Opened " + app;
        }
    }
    
    /**
     * Maps common application names to their Windows executable names
     */
    private static String getWindowsAppCommand(String appName) {
        // Remove common prefixes/suffixes and normalize
        appName = appName.replace("microsoft ", "").replace(" ms ", " ").trim();
        
        // Map common applications to their executable names
        if (appName.contains("word")) {
            return "winword";
        } else if (appName.contains("excel")) {
            return "excel";
        } else if (appName.contains("powerpoint") || appName.contains("power point")) {
            return "powerpnt";
        } else if (appName.contains("outlook")) {
            return "outlook";
        } else if (appName.contains("chrome")) {
            return "chrome";
        } else if (appName.contains("firefox")) {
            return "firefox";
        } else if (appName.contains("edge")) {
            return "msedge";
        } else if (appName.contains("notepad")) {
            return "notepad";
        } else if (appName.contains("calculator") || appName.contains("calc")) {
            return "calc";
        } else if (appName.contains("paint")) {
            return "mspaint";
        } else if (appName.contains("explorer") || appName.contains("file explorer")) {
            return "explorer";
        } else if (appName.contains("task manager")) {
            return "taskmgr";
        } else if (appName.contains("command prompt") || appName.contains("cmd")) {
            return "cmd";
        } else if (appName.contains("powershell")) {
            return "powershell";
        } else if (appName.contains("settings") || appName.contains("control panel")) {
            return "ms-settings:";
        } else if (appName.contains("discord")) {
            return "Discord";
        } else if (appName.contains("spotify")) {
            return "Spotify";
        } else if (appName.contains("steam")) {
            return "steam";
        } else if (appName.contains("vscode") || appName.contains("visual studio code")) {
            return "code";
        } else if (appName.contains("visual studio")) {
            return "devenv";
        } else if (appName.contains("teams")) {
            return "ms-teams";
        } else if (appName.contains("zoom")) {
            return "Zoom";
        }
        
        // If no mapping found, try to extract a reasonable executable name
        // Remove spaces and common words, take first meaningful word
        String[] words = appName.split("\\s+");
        if (words.length > 0) {
            return words[words.length - 1]; // Usually the last word is the app name
        }
        return appName;
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
                String stateValue = (state != null && state.equalsIgnoreCase("on")) ? "enabled" : "disabled";
                String out = execCommand(new String[]{"powershell", "-NoProfile", "-Command", script + "; if ($?) { 'ok' } else { (netsh interface set interface \"Wi-Fi\" admin=" + stateValue + ") }"});
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

    private static String getSystemInfo(String target) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                String script;
                if (target == null || target.equalsIgnoreCase("all")) {
                    script = "$cpu = Get-CimInstance Win32_Processor | Measure-Object -property LoadPercentage -Average | Select-Object -ExpandProperty Average; " +
                            "$mem = Get-CimInstance Win32_OperatingSystem | Select-Object @{Name='Used';Expression={$_.TotalVisibleMemorySize - $_.FreePhysicalMemory}}, TotalVisibleMemorySize; " +
                            "$disk = Get-CimInstance Win32_LogicalDisk -Filter \"DeviceID='C:'\" | Select-Object Size, FreeSpace; " +
                            "'CPU: ' + [math]::Round($cpu, 2) + '% | Memory: ' + [math]::Round(($mem.Used/$mem.TotalVisibleMemorySize)*100, 2) + '% | Disk: ' + [math]::Round((($disk.Size-$disk.FreeSpace)/$disk.Size)*100, 2) + '%'";
                } else if (target.equalsIgnoreCase("cpu")) {
                    script = "Get-CimInstance Win32_Processor | Measure-Object -property LoadPercentage -Average | Select-Object -ExpandProperty Average";
                } else if (target.equalsIgnoreCase("memory")) {
                    script = "$mem = Get-CimInstance Win32_OperatingSystem | Select-Object @{Name='Used';Expression={$_.TotalVisibleMemorySize - $_.FreePhysicalMemory}}, TotalVisibleMemorySize; [math]::Round(($mem.Used/$mem.TotalVisibleMemorySize)*100, 2) + '%'";
                } else if (target.equalsIgnoreCase("disk")) {
                    script = "$disk = Get-CimInstance Win32_LogicalDisk -Filter \"DeviceID='C:'\" | Select-Object Size, FreeSpace; [math]::Round((($disk.Size-$disk.FreeSpace)/$disk.Size)*100, 2) + '%'";
                } else if (target.equalsIgnoreCase("network")) {
                    script = "Get-NetAdapter | Where-Object {$_.Status -eq 'Up'} | Select-Object Name, LinkSpeed | Format-Table -AutoSize";
                } else {
                    script = "Get-ComputerInfo | Select-Object WindowsProductName, TotalPhysicalMemory, CsProcessors";
                }
                String out = execCommand(new String[]{"powershell", "-NoProfile", "-Command", script});
                return "System Info (" + target + "): " + out.trim();
            } else {
                String cmd;
                if (target == null || target.equalsIgnoreCase("all")) {
                    cmd = "echo 'CPU: ' && top -bn1 | grep 'Cpu(s)' | awk '{print $2}' && echo 'Memory: ' && free -h | grep Mem | awk '{print $3 \"/\" $2}' && echo 'Disk: ' && df -h / | tail -1 | awk '{print $5}'";
                } else if (target.equalsIgnoreCase("cpu")) {
                    cmd = "top -bn1 | grep 'Cpu(s)' | awk '{print $2}'";
                } else if (target.equalsIgnoreCase("memory")) {
                    cmd = "free -h | grep Mem | awk '{print $3 \"/\" $2}'";
                } else if (target.equalsIgnoreCase("disk")) {
                    cmd = "df -h / | tail -1 | awk '{print $5}'";
                } else if (target.equalsIgnoreCase("network")) {
                    cmd = "ip link show | grep -E '^[0-9]+:' | awk '{print $2}'";
                } else {
                    cmd = "uname -a && uptime && free -h";
                }
                String out = execCommand(new String[]{"/bin/sh", "-c", cmd});
                return "System Info (" + target + "): " + out.trim();
            }
        } catch (Exception e) {
            return "Failed to get system info: " + e.getMessage();
        }
    }

    private static String adjustVolume(String value, String direction) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                String script;
                if (direction != null && direction.equalsIgnoreCase("up")) {
                    int vol = Integer.parseInt(value);
                    script = "$obj = New-Object -ComObject Shell.Application; $obj.NameSpace(17).ParseName('').InvokeVerb('Properties'); (New-Object -ComObject WScript.Shell).SendKeys([char]175)";
                    // Alternative: use nircmd or PowerShell volume control
                    script = "$wshell = New-Object -ComObject wscript.shell; for($i=0; $i -lt " + vol + "; $i++) { $wshell.SendKeys([char]175) }";
                } else if (direction != null && direction.equalsIgnoreCase("down")) {
                    int vol = Integer.parseInt(value);
                    script = "$wshell = New-Object -ComObject wscript.shell; for($i=0; $i -lt " + vol + "; $i++) { $wshell.SendKeys([char]174) }";
                } else {
                    int vol = Integer.parseInt(value);
                    script = "$obj = New-Object -ComObject Shell.Application; (Get-WmiObject -Class Win32_Volume | Where-Object {$_.DriveLetter -eq $null}).SetVolume(" + vol + ")";
                    // Use PowerShell to set volume percentage
                    script = "$volume = (New-Object -ComObject Shell.Application).NameSpace(17).ParseName(''); $volume.InvokeVerb('Properties'); Start-Sleep -Milliseconds 500; (New-Object -ComObject WScript.Shell).SendKeys('" + vol + "{ENTER}')";
                }
                execCommand(new String[]{"powershell", "-NoProfile", "-Command", script});
                return "Volume adjusted: " + (direction != null ? direction : "set") + " " + value + "%";
            } else if (os.contains("mac")) {
                String cmd;
                if (direction != null && direction.equalsIgnoreCase("set")) {
                    cmd = "osascript -e 'set volume output volume " + value + "'";
                } else {
                    cmd = "osascript -e 'set volume output volume (output volume of (get volume settings) " + (direction != null && direction.equalsIgnoreCase("up") ? "+" : "-") + " " + value + ")'";
                }
                execCommand(new String[]{"/bin/sh", "-c", cmd});
                return "Volume adjusted: " + (direction != null ? direction : "set") + " " + value + "%";
            } else {
                String cmd = "amixer set Master " + (direction != null && direction.equalsIgnoreCase("up") ? value + "%+" : direction != null && direction.equalsIgnoreCase("down") ? value + "%-" : value + "%");
                execCommand(new String[]{"/bin/sh", "-c", cmd});
                return "Volume adjusted: " + (direction != null ? direction : "set") + " " + value + "%";
            }
        } catch (Exception e) {
            return "Failed to adjust volume: " + e.getMessage();
        }
    }

    private static String mediaControl(String target) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                String key;
                if (target.equalsIgnoreCase("play") || target.equalsIgnoreCase("pause")) {
                    key = "{MEDIA_PLAY_PAUSE}";
                } else if (target.equalsIgnoreCase("next")) {
                    key = "{MEDIA_NEXT_TRACK}";
                } else if (target.equalsIgnoreCase("previous")) {
                    key = "{MEDIA_PREV_TRACK}";
                } else if (target.equalsIgnoreCase("stop")) {
                    key = "{MEDIA_STOP}";
                } else {
                    return "Unknown media control: " + target;
                }
                String script = "(New-Object -ComObject WScript.Shell).SendKeys('" + key + "')";
                execCommand(new String[]{"powershell", "-NoProfile", "-Command", script});
                return "Media control: " + target;
            } else if (os.contains("mac")) {
                String cmd = "osascript -e 'tell application \"System Events\" to key code " +
                        (target.equalsIgnoreCase("play") || target.equalsIgnoreCase("pause") ? "179" :
                         target.equalsIgnoreCase("next") ? "144" :
                         target.equalsIgnoreCase("previous") ? "145" : "182") + "'";
                execCommand(new String[]{"/bin/sh", "-c", cmd});
                return "Media control: " + target;
            } else {
                String cmd = "qdbus org.mpris.MediaPlayer2.Player /org/mpris/MediaPlayer2 org.mpris.MediaPlayer2.Player." +
                        (target.equalsIgnoreCase("play") ? "Play" :
                         target.equalsIgnoreCase("pause") ? "Pause" :
                         target.equalsIgnoreCase("next") ? "Next" :
                         target.equalsIgnoreCase("previous") ? "Previous" :
                         target.equalsIgnoreCase("stop") ? "Stop" : "Play");
                execCommand(new String[]{"/bin/sh", "-c", cmd});
                return "Media control: " + target;
            }
        } catch (Exception e) {
            return "Media control failed: " + e.getMessage();
        }
    }

    private static String takeScreenshot() throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            if (os.contains("win")) {
                String path = System.getProperty("user.home") + "\\Desktop\\screenshot_" + timestamp + ".png";
                String script = "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; $bounds = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds; $bmp = New-Object System.Drawing.Bitmap $bounds.Width, $bounds.Height; $graphics = [System.Drawing.Graphics]::FromImage($bmp); $graphics.CopyFromScreen($bounds.Location, [System.Drawing.Point]::Empty, $bounds.Size); $bmp.Save('" + path + "'); $graphics.Dispose(); $bmp.Dispose()";
                execCommand(new String[]{"powershell", "-NoProfile", "-Command", script});
                return "Screenshot saved to: " + path;
            } else if (os.contains("mac")) {
                String path = System.getProperty("user.home") + "/Desktop/screenshot_" + timestamp + ".png";
                execCommand(new String[]{"/bin/sh", "-c", "screencapture " + path});
                return "Screenshot saved to: " + path;
            } else {
                String path = System.getProperty("user.home") + "/Desktop/screenshot_" + timestamp + ".png";
                execCommand(new String[]{"/bin/sh", "-c", "import -window root " + path});
                return "Screenshot saved to: " + path;
            }
        } catch (Exception e) {
            return "Screenshot failed: " + e.getMessage();
        }
    }

    private static String openFile(String target) throws IOException {
        if (target == null) return "No file path specified.";
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            new ProcessBuilder("explorer", target).start();
        } else if (os.contains("mac")) {
            new ProcessBuilder("/usr/bin/open", target).start();
        } else {
            new ProcessBuilder("/bin/sh", "-c", "xdg-open '" + target + "'").start();
        }
        return "Opened: " + target;
    }

    private static String createFile(String target, String value) throws IOException {
        if (target == null) return "No file path specified.";
        try {
            java.io.File file = new java.io.File(target);
            if (value != null && !value.isEmpty()) {
                try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                    writer.write(value);
                }
            } else {
                file.createNewFile();
            }
            return "Created file: " + target;
        } catch (Exception e) {
            return "Failed to create file: " + e.getMessage();
        }
    }

    private static String deleteFile(String target) throws IOException {
        if (target == null) return "No file path specified.";
        try {
            java.io.File file = new java.io.File(target);
            if (file.delete()) {
                return "Deleted: " + target;
            } else {
                return "Failed to delete: " + target;
            }
        } catch (Exception e) {
            return "Failed to delete file: " + e.getMessage();
        }
    }

    private static String listDirectory(String target) throws IOException {
        if (target == null) target = ".";
        try {
            java.io.File dir = new java.io.File(target);
            if (!dir.isDirectory()) {
                return "Not a directory: " + target;
            }
            java.io.File[] files = dir.listFiles();
            if (files == null) {
                return "Cannot list directory: " + target;
            }
            StringBuilder result = new StringBuilder("Directory contents:\n");
            for (java.io.File file : files) {
                result.append(file.isDirectory() ? "[DIR] " : "[FILE] ").append(file.getName()).append("\n");
            }
            return result.toString().trim();
        } catch (Exception e) {
            return "Failed to list directory: " + e.getMessage();
        }
    }

    private static String checkProcess(String target) throws IOException {
        if (target == null) return "No process name specified.";
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                String script = "Get-Process -Name '" + target + "' -ErrorAction SilentlyContinue | Select-Object ProcessName, Id, CPU, WorkingSet";
                String out = execCommand(new String[]{"powershell", "-NoProfile", "-Command", script});
                if (out.trim().isEmpty()) {
                    return "Process '" + target + "' is not running.";
                }
                return "Process info: " + out.trim();
            } else {
                String cmd = "ps aux | grep -i '" + target + "' | grep -v grep";
                String out = execCommand(new String[]{"/bin/sh", "-c", cmd});
                if (out.trim().isEmpty()) {
                    return "Process '" + target + "' is not running.";
                }
                return "Process info: " + out.trim();
            }
        } catch (Exception e) {
            return "Failed to check process: " + e.getMessage();
        }
    }

    private static String killProcess(String target) throws IOException {
        if (target == null) return "No process name or PID specified.";
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                String script;
                try {
                    int pid = Integer.parseInt(target);
                    script = "Stop-Process -Id " + pid + " -Force";
                } catch (NumberFormatException e) {
                    script = "Stop-Process -Name '" + target + "' -Force";
                }
                execCommand(new String[]{"powershell", "-NoProfile", "-Command", script});
                return "Killed process: " + target;
            } else {
                String cmd;
                try {
                    int pid = Integer.parseInt(target);
                    cmd = "kill -9 " + pid;
                } catch (NumberFormatException e) {
                    cmd = "pkill -9 '" + target + "'";
                }
                execCommand(new String[]{"/bin/sh", "-c", cmd});
                return "Killed process: " + target;
            }
        } catch (Exception e) {
            return "Failed to kill process: " + e.getMessage();
        }
    }

    private static String shutdown(String value) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                String script;
                if (value != null && value.equalsIgnoreCase("restart")) {
                    script = "Restart-Computer -Force";
                } else if (value != null && value.equalsIgnoreCase("sleep")) {
                    script = "Add-Type -AssemblyName System.Windows.Forms; [System.Windows.Forms.Application]::SetSuspendState([System.Windows.Forms.PowerState]::Suspend, $false, $false)";
                } else {
                    script = "Stop-Computer -Force";
                }
                execCommand(new String[]{"powershell", "-NoProfile", "-Command", script});
                return "Shutdown command executed: " + value;
            } else if (os.contains("mac")) {
                String cmd;
                if (value != null && value.equalsIgnoreCase("restart")) {
                    cmd = "sudo shutdown -r now";
                } else if (value != null && value.equalsIgnoreCase("sleep")) {
                    cmd = "pmset sleepnow";
                } else {
                    cmd = "sudo shutdown -h now";
                }
                execCommand(new String[]{"/bin/sh", "-c", cmd});
                return "Shutdown command executed: " + value;
            } else {
                String cmd;
                if (value != null && value.equalsIgnoreCase("restart")) {
                    cmd = "sudo reboot";
                } else if (value != null && value.equalsIgnoreCase("sleep")) {
                    cmd = "systemctl suspend";
                } else {
                    cmd = "sudo shutdown -h now";
                }
                execCommand(new String[]{"/bin/sh", "-c", cmd});
                return "Shutdown command executed: " + value;
            }
        } catch (Exception e) {
            return "Shutdown command failed: " + e.getMessage();
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
