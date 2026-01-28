param(
    [string]$ProjectRoot,
    [string]$ConfigFile,
    [string]$ServersRoot,
    [string]$Xms = "1G",
    [string]$Xmx = "1G",
    [int]$BootWaitSeconds = 30,
    [int]$ShutdownWaitSeconds = 10,
    [string]$OnlyVersions
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ============================================================================
# UTILITY FUNCTIONS
# ============================================================================

function Resolve-ProjectRoot {
    param([string]$RootCandidate)
    try {
        if (-not [string]::IsNullOrWhiteSpace($RootCandidate)) {
            if (Test-Path $RootCandidate) {
                return (Resolve-Path $RootCandidate).Path
            }
            return [IO.Path]::GetFullPath($RootCandidate)
        }
        return [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
    } catch {
        throw "Failed to resolve project root: $($_.Exception.Message)"
    }
}

function Resolve-OptionalPath {
    param(
        [string]$PathCandidate,
        [string]$BasePath
    )
    try {
        if ([string]::IsNullOrWhiteSpace($PathCandidate)) {
            return $null
        }
        $resolved = if ([IO.Path]::IsPathRooted($PathCandidate)) {
            $PathCandidate
        } elseif (-not [string]::IsNullOrWhiteSpace($BasePath)) {
            Join-Path $BasePath $PathCandidate
        } else {
            $PathCandidate
        }
        if (Test-Path $resolved) {
            return (Resolve-Path $resolved).Path
        }
        return [IO.Path]::GetFullPath($resolved)
    } catch {
        throw "Failed to resolve path '$PathCandidate': $($_.Exception.Message)"
    }
}

function Write-Log {
    param(
        [string]$Message,
        [string]$Level = "INFO"
    )
    try {
        $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
        $line = "[$timestamp] [$Level] $Message"

        switch ($Level) {
            "ERROR" { Write-Host $line -ForegroundColor Red }
            "WARN"  { Write-Host $line -ForegroundColor Yellow }
            "SUCCESS" { Write-Host $line -ForegroundColor Green }
            default { Write-Host $line }
        }

        if ($script:LogFile) {
            $line | Out-File -FilePath $script:LogFile -Append -Encoding UTF8
        }
    } catch {
        Write-Host "Failed to write log: $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Ensure-Directory {
    param([string]$Path)
    try {
        if (-not (Test-Path $Path)) {
            $null = New-Item -Path $Path -ItemType Directory -Force
        }
    } catch {
        throw "Failed to create directory '$Path': $($_.Exception.Message)"
    }
}

function Get-BaseVersion {
    param([string]$Version)
    if ([string]::IsNullOrWhiteSpace($Version)) {
        return $Version
    }
    return $Version.Split('-')[0]
}

function Get-VersionSortKey {
    param([string]$VersionName)
    try {
        $base = Get-BaseVersion $VersionName
        if ([string]::IsNullOrWhiteSpace($base)) {
            return [version]"0.0"
        }
        return [version]$base
    } catch {
        return [version]"0.0"
    }
}

function Get-JdkMajorForVersion {
    param([string]$Version)
    try {
        $base = Get-BaseVersion $Version
        if ([string]::IsNullOrWhiteSpace($base)) {
            return 21
        }
        $parts = $base.Split('.')
        if ($parts.Length -lt 2) {
            return 21
        }
        $minor = [int]$parts[1]
        $patch = 0
        if ($parts.Length -ge 3) {
            $patch = [int]$parts[2]
        }

        if ($minor -le 16) {
            return 8
        }
        if ($minor -eq 17) {
            return 16
        }
        if ($minor -eq 18 -or $minor -eq 19) {
            return 17
        }
        if ($minor -eq 20) {
            if ($patch -ge 5) {
                return 21
            }
            return 17
        }
        return 21
    } catch {
        Write-Log "Error determining JDK version for $Version, defaulting to JDK 21: $($_.Exception.Message)" "WARN"
        return 21
    }
}

function Resolve-JavaHome {
    param([int]$JdkMajor, [string]$Root)
    try {
        $envVar = "JAVA$JdkMajor`_HOME"
        $javaHome = [Environment]::GetEnvironmentVariable($envVar, "Process")
        if ([string]::IsNullOrWhiteSpace($javaHome)) {
            $javaHome = [Environment]::GetEnvironmentVariable($envVar, "User")
        }
        if ([string]::IsNullOrWhiteSpace($javaHome)) {
            $javaHome = [Environment]::GetEnvironmentVariable($envVar, "Machine")
        }
        if ([string]::IsNullOrWhiteSpace($javaHome)) {
            $defaultRoot = Join-Path $Root "jdks"
            $candidate = Join-Path $defaultRoot ("jdk$JdkMajor")
            if (Test-Path $candidate) {
                $javaHome = $candidate
            }
        }
        if ([string]::IsNullOrWhiteSpace($javaHome)) {
            $fallback = Join-Path $Root ("jdk$JdkMajor")
            if (Test-Path $fallback) {
                $javaHome = $fallback
            }
        }
        if ([string]::IsNullOrWhiteSpace($javaHome)) {
            return $null
        }
        $javaExe = Join-Path $javaHome "bin\java.exe"
        if (Test-Path $javaExe) {
            return $javaHome
        }
        return $null
    } catch {
        Write-Log "Error resolving Java home for JDK ${JdkMajor}: $($_.Exception.Message)" "ERROR"
        return $null
    }
}

# ============================================================================
# SERVER TESTING FUNCTIONS
# ============================================================================

function Test-ServerWithPlugin {
    param(
        [string]$JavaExe,
        [string]$JarPath,
        [string]$WorkDir,
        [string]$Xms,
        [string]$Xmx,
        [int]$BootWaitSeconds,
        [int]$ShutdownWaitSeconds
    )

    $process = $null

    try {
        Write-Log "Starting server with plugin..." "DEBUG"

        if (-not (Test-Path $JavaExe)) {
            throw "Java executable not found: $JavaExe"
        }
        if (-not (Test-Path $JarPath)) {
            throw "Server jar not found: $JarPath"
        }
        if (-not (Test-Path $WorkDir)) {
            throw "Working directory not found: $WorkDir"
        }

        # Create output capture files
        $stdoutFile = Join-Path $WorkDir "stdout.tmp.log"
        $stderrFile = Join-Path $WorkDir "stderr.tmp.log"

        if (Test-Path $stdoutFile) { Remove-Item $stdoutFile -Force }
        if (Test-Path $stderrFile) { Remove-Item $stderrFile -Force }

        # Create batch file to run the server
        $runBat = Join-Path $WorkDir "run-test.bat"
        $batContent = @"
@echo off
cd /d "$WorkDir"
"$JavaExe" -Xms$Xms -Xmx$Xmx -jar "$JarPath" --nogui 1>"$stdoutFile" 2>"$stderrFile"
"@
        Set-Content -Path $runBat -Value $batContent -Encoding ASCII

        $startInfo = New-Object System.Diagnostics.ProcessStartInfo
        $startInfo.FileName = "cmd.exe"
        $startInfo.Arguments = "/c `"$runBat`""
        $startInfo.WorkingDirectory = $WorkDir
        $startInfo.UseShellExecute = $false
        $startInfo.RedirectStandardInput = $true
        $startInfo.CreateNoWindow = $true

        $process = New-Object System.Diagnostics.Process
        $process.StartInfo = $startInfo

        $startResult = $process.Start()
        if (-not $startResult) {
            throw "Failed to start process"
        }

        Write-Log "Server started (PID: $($process.Id)), waiting $BootWaitSeconds seconds for boot..." "DEBUG"

        $exited = $process.WaitForExit($BootWaitSeconds * 1000)

        # Stop the server
        if (-not $exited -and -not $process.HasExited) {
            Write-Log "Stopping server..." "DEBUG"
            try {
                $process.Kill()
            } catch {
                Write-Log "Failed to stop cmd process: $($_.Exception.Message)" "WARN"
            }

            $null = $process.WaitForExit($ShutdownWaitSeconds * 1000)
        }

        if (-not $process.HasExited) {
            Write-Log "Force killing process..." "WARN"
            try {
                $process.Kill()
                $process.WaitForExit(2000)
            } catch {
                Write-Log "Failed to kill process: $($_.Exception.Message)" "ERROR"
            }
        }

        # Kill any remaining Java processes
        $javaProcesses = Get-Process -Name java -ErrorAction SilentlyContinue |
            Where-Object { $_.MainModule.FileName -eq $JavaExe }
        if ($javaProcesses) {
            Write-Log "Killing $(@($javaProcesses).Count) remaining Java process(es)..." "DEBUG"
            $javaProcesses | ForEach-Object {
                try {
                    $_.Kill()
                    $_.WaitForExit(2000)
                } catch { }
            }
        }

        if (-not $process.HasExited) {
            $process.WaitForExit()
        }

        # Safely get exit code (might fail if process was killed)
        $exitCode = -1
        try {
            $exitCode = $process.ExitCode
        } catch {
            Write-Log "Could not retrieve exit code (process was killed): $($_.Exception.Message)" "DEBUG"
            $exitCode = -1
        }

        Write-Log "Process exited with code: $exitCode" "DEBUG"

        # Wait for file writes
        Start-Sleep -Milliseconds 1000

        # Read captured output
        $stdoutLines = @()
        $stderrLines = @()

        if (Test-Path $stdoutFile) {
            try {
                $stdoutLines = @(Get-Content -Path $stdoutFile -ErrorAction SilentlyContinue)
                Remove-Item $stdoutFile -Force -ErrorAction SilentlyContinue
            } catch {
                Write-Log "Error reading stdout: $($_.Exception.Message)" "WARN"
            }
        }

        if (Test-Path $stderrFile) {
            try {
                $stderrLines = @(Get-Content -Path $stderrFile -ErrorAction SilentlyContinue)
                Remove-Item $stderrFile -Force -ErrorAction SilentlyContinue
            } catch {
                Write-Log "Error reading stderr: $($_.Exception.Message)" "WARN"
            }
        }

        if (Test-Path $runBat) {
            Remove-Item $runBat -Force -ErrorAction SilentlyContinue
        }

        Write-Log "Captured $($stdoutLines.Count) stdout lines, $($stderrLines.Count) stderr lines" "DEBUG"

        # Ensure we have proper arrays (not null)
        if ($null -eq $stdoutLines) { $stdoutLines = @() }
        if ($null -eq $stderrLines) { $stderrLines = @() }
        if ($null -eq $exitCode) { $exitCode = -1 }

        $result = [pscustomobject]@{
            ExitCode = [int]$exitCode
            Stdout   = [array]$stdoutLines
            Stderr   = [array]$stderrLines
        }

        return $result

    } catch {
        Write-Log "Critical error during server test: $($_.Exception.Message)" "ERROR"

        if ($process -and -not $process.HasExited) {
            try {
                $process.Kill()
                $process.WaitForExit(2000)
            } catch { }
        }

        # Kill any remaining Java processes
        try {
            $javaProcesses = Get-Process -Name java -ErrorAction SilentlyContinue |
                Where-Object { $_.MainModule.FileName -eq $JavaExe }
            if ($javaProcesses) {
                $javaProcesses | ForEach-Object {
                    try { $_.Kill() } catch { }
                }
            }
        } catch { }

        $result = [pscustomobject]@{
            ExitCode = [int]-1
            Stdout   = [array]@()
            Stderr   = [array]@()
        }

        return $result
    } finally {
        if ($process) {
            try {
                $process.Dispose()
            } catch { }
        }
    }
}

function Test-PluginErrors {
    param(
        [string[]]$StdoutLines,
        [string[]]$StderrLines
    )

    $errors = New-Object 'System.Collections.Generic.List[string]'
    $warnings = New-Object 'System.Collections.Generic.List[string]'
    $consoleErrors = New-Object 'System.Collections.Generic.List[string]'
    $pluginLoaded = $false
    $pluginEnabled = $false

    # Combine all output
    $allLines = @()
    $allLines += $StdoutLines
    $allLines += $StderrLines

    # Error patterns to look for
    $errorPatterns = @(
        'Error loading plugin',
        'Error enabling plugin',
        'Could not load plugin',
        'Plugin .* does not have a main class',
        'Exception.*loading plugin',
        'Caused by:.*Exception',
        'at org\.bukkit',
        'at net\.minecraft',
        'NoClassDefFoundError',
        'ClassNotFoundException',
        'NoSuchMethodError',
        'UnsupportedClassVersionError',
        'IncompatibleClassChangeError',
        'LinkageError',
        '\[SEVERE\]',
        '\[ERROR\]'
    )

    # Console error patterns (server thread/ERROR or /SEVERE)
    $consoleErrorPatterns = @(
        '\[[^\]]+/(ERROR|SEVERE)\]',
        '\[(ERROR|SEVERE)\]'
    )

    # Plugin success patterns
    $loadedPatterns = @(
        'Loading .* v\d',
        'Loaded plugin',
        'Loading plugin'
    )

    $enabledPatterns = @(
        'Enabling .* v\d',
        'Enabled plugin',
        'Enabling plugin'
    )

    foreach ($line in $allLines) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }

        $trimmed = $line.Trim()

        # Check for plugin loaded
        foreach ($pattern in $loadedPatterns) {
            if ($trimmed -match $pattern) {
                $pluginLoaded = $true
                break
            }
        }

        # Check for plugin enabled
        foreach ($pattern in $enabledPatterns) {
            if ($trimmed -match $pattern) {
                $pluginEnabled = $true
                break
            }
        }

        # Capture console error lines
        foreach ($pattern in $consoleErrorPatterns) {
            if ($trimmed -match $pattern) {
                if (-not $consoleErrors.Contains($trimmed)) {
                    $consoleErrors.Add($trimmed)
                }
                break
            }
        }

        # Check for errors
        foreach ($pattern in $errorPatterns) {
            if ($trimmed -match $pattern) {
                $errors.Add($trimmed)
                break
            }
        }

        # Check for warnings
        if ($trimmed -match '\[WARN\]' -or $trimmed -match '\[WARNING\]') {
            $warnings.Add($trimmed)
        }
    }

    return [pscustomobject]@{
        PluginLoaded  = $pluginLoaded
        PluginEnabled = $pluginEnabled
        Errors        = $errors
        Warnings      = $warnings
        ConsoleErrors = $consoleErrors
        TotalLines    = $allLines.Count
    }
}

# ============================================================================
# MAIN SCRIPT EXECUTION
# ============================================================================

try {
    $projectRoot = Resolve-ProjectRoot $ProjectRoot

    # Load config file if specified or use default
    $configPath = if ([string]::IsNullOrWhiteSpace($ConfigFile)) {
        Join-Path $projectRoot "scripts\test-paper-config.json"
    } else {
        Resolve-OptionalPath $ConfigFile $projectRoot
    }

    $config = $null
    if ($configPath -and (Test-Path $configPath)) {
        try {
            $config = Get-Content -Path $configPath -Raw | ConvertFrom-Json
        } catch {
            throw "Failed to parse config file: $($_.Exception.Message)"
        }
    } elseif (-not [string]::IsNullOrWhiteSpace($ConfigFile)) {
        throw "Config file not found: $configPath"
    }

    # Apply config values if not overridden by command-line parameters
    if ($config) {
        if (-not $PSBoundParameters.ContainsKey('ProjectRoot') -and -not [string]::IsNullOrWhiteSpace($config.projectRoot)) {
            $projectRoot = Resolve-ProjectRoot $config.projectRoot
        }
        if (-not $PSBoundParameters.ContainsKey('ServersRoot') -and -not [string]::IsNullOrWhiteSpace($config.serversRoot)) {
            $ServersRoot = $config.serversRoot
        }
        if (-not $PSBoundParameters.ContainsKey('OnlyVersions') -and -not [string]::IsNullOrWhiteSpace($config.onlyVersions)) {
            $OnlyVersions = $config.onlyVersions
        }
        if (-not $PSBoundParameters.ContainsKey('Xms') -and -not [string]::IsNullOrWhiteSpace($config.xms)) {
            $Xms = $config.xms
        }
        if (-not $PSBoundParameters.ContainsKey('Xmx') -and -not [string]::IsNullOrWhiteSpace($config.xmx)) {
            $Xmx = $config.xmx
        }
        if (-not $PSBoundParameters.ContainsKey('BootWaitSeconds') -and $null -ne $config.bootWaitSeconds) {
            $BootWaitSeconds = [int]$config.bootWaitSeconds
        }
        if (-not $PSBoundParameters.ContainsKey('ShutdownWaitSeconds') -and $null -ne $config.shutdownWaitSeconds) {
            $ShutdownWaitSeconds = [int]$config.shutdownWaitSeconds
        }
    }

    # Setup logging FIRST before any operations that might throw errors
    $logDir = Join-Path $projectRoot "scripts\logs"
    Ensure-Directory -Path $logDir
    $script:LogFile = Join-Path $logDir ("plugin-test-{0}.log" -f (Get-Date -Format "yyyyMMdd-HHmmss"))

    $serversRoot = if ([string]::IsNullOrWhiteSpace($ServersRoot)) {
        Join-Path $projectRoot "paper-servers"
    } else {
        Resolve-OptionalPath $ServersRoot $projectRoot
    }

    if (-not (Test-Path $serversRoot)) {
        throw "Servers root directory not found: $serversRoot`n`nPlease specify the correct path using -ServersRoot parameter or in the config file.`nExample: .\test-plugin-compatibility.ps1 -ServersRoot 'G:\Path\To\Servers'`nOr add `"serversRoot`": `"G:\\Path\\To\\Servers`" to scripts\test-paper-config.json"
    }

    Write-Log "==============================================="
    Write-Log "Plugin Compatibility Testing Script"
    Write-Log "==============================================="
    Write-Log "Project root: $projectRoot"
    if ($configPath -and (Test-Path $configPath)) {
        Write-Log "Config file: $configPath"
    } else {
        Write-Log "Config file: (none)"
    }
    Write-Log "Servers root: $serversRoot"
    Write-Log "Memory settings: -Xms$Xms -Xmx$Xmx"
    Write-Log "Boot wait: $BootWaitSeconds seconds"
    Write-Log "Shutdown wait: $ShutdownWaitSeconds seconds"
    Write-Log "==============================================="

    # Find all server directories
    $serverDirs = Get-ChildItem -Path $serversRoot -Directory |
        Sort-Object `
            @{ Expression = { Get-VersionSortKey $_.Name }; Descending = $true }, `
            @{ Expression = { $_.Name }; Descending = $true }

    if (-not [string]::IsNullOrWhiteSpace($OnlyVersions)) {
        $requested = $OnlyVersions.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ }
        $serverDirs = $serverDirs | Where-Object { $requested -contains $_.Name }
    }

    if (-not $serverDirs -or $serverDirs.Count -eq 0) {
        throw "No server directories found in: $serversRoot"
    }

    Write-Log "Found $($serverDirs.Count) server(s) to test"
    Write-Log ""

    $results = New-Object 'System.Collections.Generic.List[object]'

    foreach ($serverDir in $serverDirs) {
        $version = $serverDir.Name
        $serverPath = $serverDir.FullName
        $status = "UNKNOWN"
        $details = New-Object 'System.Collections.Generic.List[string]'

        Write-Log "==============================================="
        Write-Log "Testing version: $version"
        Write-Log "==============================================="

        try {
            # Check if server jar exists
            $jarPath = Join-Path $serverPath "paper.jar"
            if (-not (Test-Path $jarPath)) {
                throw "Server jar not found: $jarPath"
            }

            # Check if plugin exists
            $pluginsDir = Join-Path $serverPath "plugins"
            if (-not (Test-Path $pluginsDir)) {
                throw "Plugins directory not found - server may not be initialized"
            }

            $pluginJars = @(Get-ChildItem -Path $pluginsDir -Filter "*.jar" -ErrorAction SilentlyContinue)
            if ($pluginJars.Count -eq 0) {
                throw "No plugin jar found in plugins directory"
            }

            Write-Log "Found $($pluginJars.Count) plugin(s): $($pluginJars.Name -join ', ')"

            # Determine required JDK
            $jdkMajor = Get-JdkMajorForVersion -Version $version
            Write-Log "Required JDK version: $jdkMajor"

            $javaHome = Resolve-JavaHome -JdkMajor $jdkMajor -Root $projectRoot
            if (-not $javaHome) {
                throw "Missing JDK $jdkMajor (set JAVA${jdkMajor}_HOME or place in jdks\jdk$jdkMajor)"
            }

            $javaExe = Join-Path $javaHome "bin\java.exe"
            if (-not (Test-Path $javaExe)) {
                throw "java.exe not found: $javaExe"
            }

            Write-Log "Using Java: $javaHome"

            # Run the server with plugin
            Write-Log "Starting server..."
            $runResult = Test-ServerWithPlugin `
                -JavaExe $javaExe `
                -JarPath $jarPath `
                -WorkDir $serverPath `
                -Xms $Xms `
                -Xmx $Xmx `
                -BootWaitSeconds $BootWaitSeconds `
                -ShutdownWaitSeconds $ShutdownWaitSeconds

            if ($null -eq $runResult) {
                throw "Server test returned null result"
            }

            # Verify the result object has the expected properties
            if (-not ($runResult.PSObject.Properties.Name -contains 'ExitCode')) {
                Write-Log "WARNING: Result object missing ExitCode property. Object type: $($runResult.GetType().FullName)" "ERROR"
                Write-Log "Available properties: $($runResult.PSObject.Properties.Name -join ', ')" "ERROR"
                throw "Server test returned invalid result object (missing ExitCode property)"
            }

            Write-Log "Server stopped. Exit code: $($runResult.ExitCode)"

            # Analyze the output
            Write-Log "Analyzing output..."
            $stdoutArray = if ($runResult.Stdout) { $runResult.Stdout } else { @() }
            $stderrArray = if ($runResult.Stderr) { $runResult.Stderr } else { @() }
            $analysis = Test-PluginErrors -StdoutLines $stdoutArray -StderrLines $stderrArray

            Write-Log "Plugin loaded: $($analysis.PluginLoaded)"
            Write-Log "Plugin enabled: $($analysis.PluginEnabled)"
            $errorsArray = if ($analysis.Errors) { @($analysis.Errors) } else { @() }
            $consoleErrors = if ($analysis.ConsoleErrors) { @($analysis.ConsoleErrors) } else { @() }

            if ($consoleErrors.Count -gt 0) {
                Write-Log "Server console errors:" "ERROR"
                foreach ($consoleError in $consoleErrors) {
                    Write-Log "  $consoleError" "ERROR"
                }
            }

            $allErrors = New-Object 'System.Collections.Generic.List[string]'
            foreach ($err in $errorsArray) {
                if (-not $allErrors.Contains($err)) {
                    $allErrors.Add($err)
                }
            }
            foreach ($err in $consoleErrors) {
                if (-not $allErrors.Contains($err)) {
                    $allErrors.Add($err)
                }
            }

            Write-Log "Errors found: $($allErrors.Count) (console errors: $($consoleErrors.Count))"
            Write-Log "Warnings found: $($analysis.Warnings.Count)"

            # Determine status
            if ($allErrors.Count -gt 0) {
                $status = "FAIL"
                $details.Add("Errors: $($allErrors.Count)")

                $nonConsoleErrors = $errorsArray | Where-Object { -not ($consoleErrors -contains $_) }
                if ($nonConsoleErrors.Count -gt 0) {
                    # Log first 5 non-console error lines (stack traces, etc.)
                    $errorCount = [Math]::Min(5, $nonConsoleErrors.Count)
                    for ($i = 0; $i -lt $errorCount; $i++) {
                        Write-Log "  ERROR: $($nonConsoleErrors[$i])" "ERROR"
                    }
                    if ($nonConsoleErrors.Count -gt 5) {
                        Write-Log "  ... and $($nonConsoleErrors.Count - 5) more errors" "ERROR"
                    }
                }
            } elseif (-not $analysis.PluginLoaded) {
                $status = "FAIL"
                $details.Add("Plugin not loaded")
                Write-Log "Plugin was not loaded" "ERROR"
            } elseif (-not $analysis.PluginEnabled) {
                $status = "WARN"
                $details.Add("Plugin loaded but not enabled")
                Write-Log "Plugin loaded but not enabled" "WARN"
            } else {
                $status = "SUCCESS"
                $details.Add("Plugin loaded and enabled")
                Write-Log "Plugin loaded and enabled successfully" "SUCCESS"
            }

            if ($analysis.Warnings.Count -gt 0) {
                $details.Add("Warnings: $($analysis.Warnings.Count)")
                Write-Log "Found $($analysis.Warnings.Count) warnings" "WARN"
            }

        } catch {
            $status = "ERROR"
            $errorMsg = $_.Exception.Message
            $details.Add("Test error: $errorMsg")
            Write-Log "ERROR: $errorMsg" "ERROR"
        }

        $result = [pscustomobject]@{
            Version = $version
            Status  = $status
            Details = $details
        }
        $results.Add($result)

        Write-Log "[$version] $status - $($details -join '; ')"
        Write-Log ""
    }

    # Final summary
    Write-Log "==============================================="
    Write-Log "FINAL RESULTS"
    Write-Log "==============================================="

    foreach ($result in $results) {
        $color = switch ($result.Status) {
            "SUCCESS" { "SUCCESS" }
            "WARN"    { "WARN" }
            "FAIL"    { "ERROR" }
            "ERROR"   { "ERROR" }
            default   { "INFO" }
        }
        Write-Log "[$($result.Version)] $($result.Status) - $($result.Details -join '; ')" $color
    }

    $successCount = @($results | Where-Object { $_.Status -eq "SUCCESS" }).Count
    $warnCount = @($results | Where-Object { $_.Status -eq "WARN" }).Count
    $failCount = @($results | Where-Object { $_.Status -eq "FAIL" }).Count
    $errorCount = @($results | Where-Object { $_.Status -eq "ERROR" }).Count

    Write-Log "==============================================="
    Write-Log "Summary: $successCount SUCCESS, $warnCount WARN, $failCount FAIL, $errorCount ERROR"
    Write-Log "Log file: $script:LogFile"
    Write-Log "==============================================="

    if ($failCount -gt 0 -or $errorCount -gt 0) {
        exit 1
    }

} catch {
    $criticalError = $_.Exception.Message
    Write-Log "===============================================" "ERROR"
    Write-Log "CRITICAL ERROR - Script failed" "ERROR"
    Write-Log $criticalError "ERROR"
    if ($_.ScriptStackTrace) {
        Write-Log "Stack trace: $($_.ScriptStackTrace)" "ERROR"
    }
    Write-Log "===============================================" "ERROR"
    if ($script:LogFile) {
        Write-Log "Log file: $script:LogFile" "ERROR"
    }
    exit 1
}
