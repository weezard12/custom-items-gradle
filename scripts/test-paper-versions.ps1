param(
    [string]$ProjectRoot,
    [string]$ConfigFile,
    [string]$VersionsFile,
    [string]$ServersRoot,
    [string]$GradleExecutable,
    [string]$OnlyVersions,
    [switch]$SkipBuild,
    [switch]$SkipServerSetup,
    [switch]$SkipDownload,
    [switch]$ForceDownload,
    [switch]$SkipCopy,
    [string]$Xms = "1G",
    [string]$Xmx = "1G",
    [int]$BootWaitSeconds = 25,
    [int]$ShutdownWaitSeconds = 10
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

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

        # Write to console
        switch ($Level) {
            "ERROR" { Write-Host $line -ForegroundColor Red }
            "WARN"  { Write-Host $line -ForegroundColor Yellow }
            default { Write-Host $line }
        }

        # Write to log file if available
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
            Write-Log "Created directory: $Path" "DEBUG"
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

function Resolve-JavaExe {
    param([int]$JdkMajor, [string]$Root)
    try {
        $javaHome = Resolve-JavaHome -JdkMajor $JdkMajor -Root $Root
        if ([string]::IsNullOrWhiteSpace($javaHome)) {
            return $null
        }
        $javaExe = Join-Path $javaHome "bin\java.exe"
        if (Test-Path $javaExe) {
            return $javaExe
        }
        return $null
    } catch {
        Write-Log "Error resolving Java executable for JDK ${JdkMajor}: $($_.Exception.Message)" "ERROR"
        return $null
    }
}

function Ensure-StartBat {
    param(
        [string]$ServerDir,
        [string]$JavaHome,
        [string]$JarName,
        [string]$Xms,
        [string]$Xmx
    )
    try {
        $startBat = Join-Path $ServerDir "start.bat"
        $lines = @(
            "@echo off",
            "setlocal",
            ("set `"JAVA_HOME=$JavaHome`""),
            ("`"%JAVA_HOME%\\bin\\java.exe`" -Xms$Xms -Xmx$Xmx -jar `"$JarName`" --nogui")
        )
        Set-Content -Path $startBat -Encoding ASCII -Value $lines
        Write-Log "Created start.bat at: $startBat" "DEBUG"
    } catch {
        throw "Failed to create start.bat: $($_.Exception.Message)"
    }
}

function Ensure-EulaAccepted {
    param([string]$ServerDir)
    try {
        $eulaPath = Join-Path $ServerDir "eula.txt"
        $stamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
        $lines = @(
            "# Accepted by test-paper-versions.ps1 on $stamp",
            "eula=true"
        )
        Set-Content -Path $eulaPath -Encoding ASCII -Value $lines
        Write-Log "Created/updated eula.txt at: $eulaPath" "DEBUG"
    } catch {
        throw "Failed to create eula.txt: $($_.Exception.Message)"
    }
}

function Invoke-Download {
    param(
        [string]$Uri,
        [string]$OutFile
    )
    try {
        Write-Log "Downloading from: $Uri" "DEBUG"
        $params = @{
            Uri     = $Uri
            OutFile = $OutFile
        }
        if ($PSVersionTable.PSVersion.Major -lt 6) {
            $params.UseBasicParsing = $true
        }
        Invoke-WebRequest @params

        if (-not (Test-Path $OutFile)) {
            throw "Download completed but file not found at: $OutFile"
        }

        $fileSize = (Get-Item $OutFile).Length
        if ($fileSize -eq 0) {
            throw "Downloaded file is empty"
        }

        Write-Log "Download successful. File size: $fileSize bytes" "DEBUG"
    } catch {
        if (Test-Path $OutFile) {
            Remove-Item $OutFile -Force -ErrorAction SilentlyContinue
        }
        throw "Download failed: $($_.Exception.Message)"
    }
}

function Add-LogLine {
    param(
        [System.Collections.Generic.List[string]]$Buffer,
        [string]$Line,
        [int]$MaxLines
    )
    try {
        if ($Buffer.Count -ge $MaxLines) {
            $Buffer.RemoveAt(0)
        }
        $Buffer.Add($Line)
    } catch {
        Write-Log "Error adding log line to buffer: $($_.Exception.Message)" "WARN"
    }
}

function Invoke-ServerRun {
    param(
        [string]$JavaExe,
        [string]$JarPath,
        [string]$WorkDir,
        [string]$Xms,
        [string]$Xmx,
        [int]$BootWaitSeconds,
        [int]$ShutdownWaitSeconds,
        [int]$MaxOutputLines = 200
    )

    $process = $null

    try {
        Write-Log "Starting server process..." "DEBUG"
        Write-Log "Java: $JavaExe" "DEBUG"
        Write-Log "Jar: $JarPath" "DEBUG"
        Write-Log "WorkDir: $WorkDir" "DEBUG"
        Write-Log "Memory: -Xms$Xms -Xmx$Xmx" "DEBUG"

        # Validate inputs
        if (-not (Test-Path $JavaExe)) {
            throw "Java executable not found: $JavaExe"
        }
        if (-not (Test-Path $JarPath)) {
            throw "Server jar not found: $JarPath"
        }
        if (-not (Test-Path $WorkDir)) {
            throw "Working directory not found: $WorkDir"
        }

        # Create output capture files in work directory
        $stdoutFile = Join-Path $WorkDir "stdout.tmp.log"
        $stderrFile = Join-Path $WorkDir "stderr.tmp.log"

        # Remove old files if they exist
        if (Test-Path $stdoutFile) { Remove-Item $stdoutFile -Force }
        if (Test-Path $stderrFile) { Remove-Item $stderrFile -Force }

        # Create batch file to run the server with output redirection
        $runBat = Join-Path $WorkDir "run-temp.bat"
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

        Write-Log "Process started with PID: $($process.Id)" "DEBUG"
        Write-Log "Waiting for server boot (max $BootWaitSeconds seconds)..." "DEBUG"

        $exited = $process.WaitForExit($BootWaitSeconds * 1000)

        if (-not $exited -and -not $process.HasExited) {
            Write-Log "Server still running after boot wait, attempting to stop..." "DEBUG"

            # Find the Java process and send stop command to it
            $javaProcesses = Get-Process -Name java -ErrorAction SilentlyContinue |
                Where-Object { $_.MainModule.FileName -eq $JavaExe }

            if ($javaProcesses) {
                Write-Log "Found $($javaProcesses.Count) Java process(es), attempting graceful stop..." "DEBUG"
                # Try to stop gracefully by killing the cmd process, which should stop java
                try {
                    $process.Kill()
                } catch {
                    Write-Log "Failed to stop cmd process: $($_.Exception.Message)" "WARN"
                }
            }

            Write-Log "Waiting for graceful shutdown (max $ShutdownWaitSeconds seconds)..." "DEBUG"
            $null = $process.WaitForExit($ShutdownWaitSeconds * 1000)
        }

        if (-not $process.HasExited) {
            Write-Log "Process did not stop gracefully, killing..." "WARN"
            try {
                $process.Kill()
                $process.WaitForExit(2000)
            } catch {
                Write-Log "Failed to kill process: $($_.Exception.Message)" "ERROR"
            }
        }

        # Make sure all Java processes are stopped
        $javaProcesses = Get-Process -Name java -ErrorAction SilentlyContinue |
            Where-Object { $_.MainModule.FileName -eq $JavaExe }
        if ($javaProcesses) {
            Write-Log "Killing remaining Java processes..." "WARN"
            $javaProcesses | ForEach-Object {
                try {
                    $_.Kill()
                    $_.WaitForExit(2000)
                } catch {
                    Write-Log "Failed to kill Java process $($_.Id): $($_.Exception.Message)" "WARN"
                }
            }
        }

        # Ensure process has fully exited
        if (-not $process.HasExited) {
            $process.WaitForExit()
        }

        $exitCode = $process.ExitCode
        Write-Log "Process exited with code: $exitCode" "DEBUG"

        # Wait a moment for file writes to complete
        Start-Sleep -Milliseconds 1000

        # Read captured output
        $stdoutLines = @()
        $stderrLines = @()

        if (Test-Path $stdoutFile) {
            try {
                $stdoutLines = @(Get-Content -Path $stdoutFile -ErrorAction SilentlyContinue)
                Remove-Item $stdoutFile -Force -ErrorAction SilentlyContinue
            } catch {
                Write-Log "Error reading stdout file: $($_.Exception.Message)" "WARN"
            }
        }

        if (Test-Path $stderrFile) {
            try {
                $stderrLines = @(Get-Content -Path $stderrFile -ErrorAction SilentlyContinue)
                Remove-Item $stderrFile -Force -ErrorAction SilentlyContinue
            } catch {
                Write-Log "Error reading stderr file: $($_.Exception.Message)" "WARN"
            }
        }

        # Clean up batch file
        if (Test-Path $runBat) {
            Remove-Item $runBat -Force -ErrorAction SilentlyContinue
        }

        Write-Log "Captured $($stdoutLines.Count) stdout lines, $($stderrLines.Count) stderr lines" "DEBUG"

        $stdoutList = New-Object 'System.Collections.Generic.List[string]'
        foreach ($line in $stdoutLines) {
            if ($line) { $stdoutList.Add($line) }
        }

        $stderrList = New-Object 'System.Collections.Generic.List[string]'
        foreach ($line in $stderrLines) {
            if ($line) { $stderrList.Add($line) }
        }

        return [pscustomobject]@{
            ExitCode = $exitCode
            Stdout   = $stdoutList
            Stderr   = $stderrList
        }

    } catch {
        Write-Log "Critical error during server run: $($_.Exception.Message)" "ERROR"
        Write-Log "Error details: $($_.Exception.ToString())" "DEBUG"

        if ($process -and -not $process.HasExited) {
            try {
                Write-Log "Attempting to kill process due to error..." "WARN"
                $process.Kill()
                $process.WaitForExit(2000)
            } catch {
                Write-Log "Failed to kill process during error cleanup: $($_.Exception.Message)" "ERROR"
            }
        }

        # Try to kill any remaining Java processes
        try {
            $javaProcesses = Get-Process -Name java -ErrorAction SilentlyContinue |
                Where-Object { $_.MainModule.FileName -eq $JavaExe }
            if ($javaProcesses) {
                $javaProcesses | ForEach-Object {
                    try { $_.Kill() } catch { }
                }
            }
        } catch { }

        $stdoutList = New-Object 'System.Collections.Generic.List[string]'
        $stderrList = New-Object 'System.Collections.Generic.List[string]'

        return [pscustomobject]@{
            ExitCode = -1
            Stdout   = $stdoutList
            Stderr   = $stderrList
        }
    } finally {
        if ($process) {
            try {
                $process.Dispose()
            } catch {
                Write-Log "Error disposing process: $($_.Exception.Message)" "WARN"
            }
        }
    }
}

function Ensure-ServerInitialized {
    param(
        [string]$ServerDir,
        [string]$JavaExe,
        [string]$JarPath,
        [string]$Xms,
        [string]$Xmx,
        [int]$BootWaitSeconds,
        [int]$ShutdownWaitSeconds
    )

    try {
        $markerPath = Join-Path $ServerDir ".paper-initialized"
        if (Test-Path $markerPath) {
            Write-Log "Server already initialized: $ServerDir"
            return
        }

        Write-Log "Starting initial server run (generate files): $ServerDir"
        $firstRun = Invoke-ServerRun -JavaExe $JavaExe -JarPath $JarPath -WorkDir $ServerDir -Xms $Xms -Xmx $Xmx -BootWaitSeconds $BootWaitSeconds -ShutdownWaitSeconds $ShutdownWaitSeconds

        if ($firstRun.ExitCode -eq -1) {
            $tail = ($firstRun.Stderr + $firstRun.Stdout) | Select-Object -Last 20
            throw "First run failed with critical error. Last 20 lines: $($tail -join ' | ')"
        }

        Write-Log "First run completed. Exit code: $($firstRun.ExitCode)" "DEBUG"

        # Log last few lines for debugging
        $lastStdout = $firstRun.Stdout | Select-Object -Last 5
        $lastStderr = $firstRun.Stderr | Select-Object -Last 5
        if ($lastStdout.Count -gt 0) {
            Write-Log "Last stdout lines: $($lastStdout -join ' | ')" "DEBUG"
        }
        if ($lastStderr.Count -gt 0) {
            Write-Log "Last stderr lines: $($lastStderr -join ' | ')" "DEBUG"
        }

        Ensure-EulaAccepted -ServerDir $ServerDir

        Write-Log "Starting second server run (post-EULA): $ServerDir"
        $secondRun = Invoke-ServerRun -JavaExe $JavaExe -JarPath $JarPath -WorkDir $ServerDir -Xms $Xms -Xmx $Xmx -BootWaitSeconds $BootWaitSeconds -ShutdownWaitSeconds $ShutdownWaitSeconds

        Write-Log "Second run completed. Exit code: $($secondRun.ExitCode)" "DEBUG"

        if ($secondRun.ExitCode -ne 0) {
            $tail = ($secondRun.Stderr + $secondRun.Stdout) | Select-Object -Last 20
            throw "Server run exit code $($secondRun.ExitCode). Last 20 lines: $($tail -join ' | ')"
        }

        $stamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
        Set-Content -Path $markerPath -Encoding ASCII -Value "Initialized on $stamp"
        Write-Log "Server initialization complete. Marker created at: $markerPath" "DEBUG"

    } catch {
        throw "Server initialization failed: $($_.Exception.Message)"
    }
}

function Get-LatestPluginJar {
    param([string]$Root)
    try {
        $outputDir = Join-Path $Root "plug-in\build\libs"
        if (-not (Test-Path $outputDir)) {
            Write-Log "Plugin output directory not found: $outputDir" "WARN"
            return $null
        }
        $jar = Get-ChildItem -Path $outputDir -Filter "*-all.jar" -ErrorAction SilentlyContinue |
               Sort-Object LastWriteTime -Descending |
               Select-Object -First 1
        if (-not $jar) {
            $jar = Get-ChildItem -Path $outputDir -Filter "*.jar" -ErrorAction SilentlyContinue |
                   Sort-Object LastWriteTime -Descending |
                   Select-Object -First 1
        }
        return $jar
    } catch {
        Write-Log "Error finding plugin jar: $($_.Exception.Message)" "ERROR"
        return $null
    }
}

function Build-Plugin {
    param(
        [string]$Root,
        [string]$GradleTask,
        [string]$GradleExecutable
    )
    try {
        $buildScript = Join-Path $Root "scripts\build-plugin.ps1"
        if (-not (Test-Path $buildScript)) {
            throw "Build script not found: $buildScript"
        }

        Write-Log "Building plugin via scripts\build-plugin.ps1"
        $buildArgs = @{ ProjectRoot = $Root; GradleTask = $GradleTask }
        if (-not [string]::IsNullOrWhiteSpace($GradleExecutable)) {
            $buildArgs.GradleExecutable = $GradleExecutable
        }

        & $buildScript @buildArgs

        if ($LASTEXITCODE -ne 0) {
            throw "Plugin build failed with exit code $LASTEXITCODE"
        }

        $jar = Get-LatestPluginJar -Root $Root
        if (-not $jar) {
            throw "Build completed, but no jar found in plug-in\build\libs"
        }

        Write-Log "Plugin built successfully: $($jar.FullName)" "DEBUG"
        return $jar.FullName
    } catch {
        throw "Plugin build error: $($_.Exception.Message)"
    }
}

# ============================================================================
# MAIN SCRIPT EXECUTION
# ============================================================================

try {
    $projectRoot = Resolve-ProjectRoot $ProjectRoot

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

    if ($config) {
        if (-not $PSBoundParameters.ContainsKey('ProjectRoot') -and -not [string]::IsNullOrWhiteSpace($config.projectRoot)) {
            $projectRoot = Resolve-ProjectRoot $config.projectRoot
        }
        if (-not $PSBoundParameters.ContainsKey('VersionsFile') -and -not [string]::IsNullOrWhiteSpace($config.versionsFile)) {
            $VersionsFile = $config.versionsFile
        }
        if (-not $PSBoundParameters.ContainsKey('ServersRoot') -and -not [string]::IsNullOrWhiteSpace($config.serversRoot)) {
            $ServersRoot = $config.serversRoot
        }
        if (-not $PSBoundParameters.ContainsKey('GradleExecutable') -and -not [string]::IsNullOrWhiteSpace($config.gradleExecutable)) {
            $GradleExecutable = $config.gradleExecutable
        }
        if (-not $PSBoundParameters.ContainsKey('OnlyVersions') -and -not [string]::IsNullOrWhiteSpace($config.onlyVersions)) {
            $OnlyVersions = $config.onlyVersions
        }
        if (-not $PSBoundParameters.ContainsKey('SkipBuild') -and $null -ne $config.skipBuild) {
            $SkipBuild = [bool]$config.skipBuild
        }
        if (-not $PSBoundParameters.ContainsKey('SkipServerSetup') -and $null -ne $config.skipServerSetup) {
            $SkipServerSetup = [bool]$config.skipServerSetup
        }
        if (-not $PSBoundParameters.ContainsKey('SkipDownload') -and $null -ne $config.skipDownload) {
            $SkipDownload = [bool]$config.skipDownload
        }
        if (-not $PSBoundParameters.ContainsKey('ForceDownload') -and $null -ne $config.forceDownload) {
            $ForceDownload = [bool]$config.forceDownload
        }
        if (-not $PSBoundParameters.ContainsKey('SkipCopy') -and $null -ne $config.skipCopy) {
            $SkipCopy = [bool]$config.skipCopy
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

    $versionsPath = if ([string]::IsNullOrWhiteSpace($VersionsFile)) {
        Join-Path $projectRoot "scripts\paper-versions.json"
    } else {
        Resolve-OptionalPath $VersionsFile $projectRoot
    }

    if (-not (Test-Path $versionsPath)) {
        throw "Versions file not found: $versionsPath"
    }

    $serversRoot = if ([string]::IsNullOrWhiteSpace($ServersRoot)) {
        Join-Path $projectRoot "paper-servers"
    } else {
        Resolve-OptionalPath $ServersRoot $projectRoot
    }

    if (-not [string]::IsNullOrWhiteSpace($GradleExecutable)) {
        $GradleExecutable = Resolve-OptionalPath $GradleExecutable $projectRoot
    }

    $logDir = Join-Path $projectRoot "scripts\logs"
    Ensure-Directory -Path $logDir
    $script:LogFile = Join-Path $logDir ("paper-test-{0}.log" -f (Get-Date -Format "yyyyMMdd-HHmmss"))

    Write-Log "==============================================="
    Write-Log "Paper Server Version Testing Script"
    Write-Log "==============================================="
    Write-Log "Project root: $projectRoot"
    if ($configPath -and (Test-Path $configPath)) {
        Write-Log "Config file: $configPath"
    } else {
        Write-Log "Config file: (none)"
    }
    Write-Log "Versions file: $versionsPath"
    Write-Log "Servers root: $serversRoot"
    Write-Log "Memory settings: -Xms$Xms -Xmx$Xmx"
    Write-Log "Boot wait: $BootWaitSeconds seconds"
    Write-Log "Shutdown wait: $ShutdownWaitSeconds seconds"
    Write-Log "==============================================="

    try {
        $versionsData = Get-Content -Path $versionsPath -Raw | ConvertFrom-Json
    } catch {
        throw "Failed to parse versions file: $($_.Exception.Message)"
    }

    $versionEntries = $versionsData.versions.PSObject.Properties
    $versionEntries = $versionEntries | Where-Object { $_.Name -notmatch '(?i)-pre' }

    if (-not [string]::IsNullOrWhiteSpace($OnlyVersions)) {
        $requested = $OnlyVersions.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ }
        $versionEntries = $versionEntries | Where-Object { $requested -contains $_.Name }
        $missing = $requested | Where-Object { $versionEntries.Name -notcontains $_ }
        if ($missing.Count -gt 0) {
            Write-Log "Requested versions not found in versions file: $($missing -join ', ')" "WARN"
        }
    }

    if (-not $versionEntries -or $versionEntries.Count -eq 0) {
        throw "No versions to process after filtering."
    }

    Write-Log "Processing $($versionEntries.Count) versions"
    Ensure-Directory -Path $serversRoot

    $results = New-Object 'System.Collections.Generic.List[object]'

    foreach ($entry in $versionEntries) {
        $version = $entry.Name
        $url = $entry.Value
        $status = "OK"
        $notes = New-Object 'System.Collections.Generic.List[string]'

        Write-Log "-----------------------------------------------"
        Write-Log "Processing version: $version"
        Write-Log "-----------------------------------------------"

        try {
            $serverDir = Join-Path $serversRoot $version
            Ensure-Directory -Path $serverDir

            $jarPath = Join-Path $serverDir "paper.jar"
            if (-not $SkipDownload) {
                if ($ForceDownload -or -not (Test-Path $jarPath)) {
                    Write-Log "Downloading $version from $url"
                    $tempPath = "$jarPath.tmp"
                    if (Test-Path $tempPath) {
                        Remove-Item $tempPath -Force
                    }
                    Invoke-Download -Uri $url -OutFile $tempPath
                    Move-Item -Path $tempPath -Destination $jarPath -Force
                    $notes.Add("Downloaded")
                } else {
                    Write-Log "Jar already exists, skipping download"
                    $notes.Add("Jar exists")
                }
            } elseif (-not (Test-Path $jarPath)) {
                throw "SkipDownload set but jar missing: $jarPath"
            }

            $jdkMajor = Get-JdkMajorForVersion -Version $version
            Write-Log "Required JDK version: $jdkMajor"

            $javaHome = Resolve-JavaHome -JdkMajor $jdkMajor -Root $projectRoot
            if (-not $javaHome) {
                throw "Missing JDK $jdkMajor for $version (set JAVA${jdkMajor}_HOME or place jdks\jdk$jdkMajor)."
            }

            Write-Log "Using Java home: $javaHome"
            $javaExe = Join-Path $javaHome "bin\java.exe"
            if (-not (Test-Path $javaExe)) {
                throw "java.exe not found for JDK $jdkMajor at $javaExe"
            }

            Ensure-StartBat -ServerDir $serverDir -JavaHome $javaHome -JarName "paper.jar" -Xms $Xms -Xmx $Xmx
            $notes.Add("start.bat ready")

            if (-not $SkipServerSetup) {
                Ensure-ServerInitialized -ServerDir $serverDir -JavaExe $javaExe -JarPath $jarPath -Xms $Xms -Xmx $Xmx -BootWaitSeconds $BootWaitSeconds -ShutdownWaitSeconds $ShutdownWaitSeconds
                $notes.Add("initialized")
            } else {
                Write-Log "Server setup skipped (SkipServerSetup flag set)"
                $notes.Add("server setup skipped")
            }

        } catch {
            $status = "ERROR"
            $errorMsg = $_.Exception.Message
            $notes.Add($errorMsg)
            Write-Log "ERROR processing $version : $errorMsg" "ERROR"

            # Log stack trace for debugging
            if ($_.ScriptStackTrace) {
                Write-Log "Stack trace: $($_.ScriptStackTrace)" "DEBUG"
            }
        }

        $result = [pscustomobject]@{
            Version = $version
            Status  = $status
            Notes   = $notes
        }
        $results.Add($result)

        if ($status -eq "OK") {
            Write-Log "[$version] SETUP OK - $($result.Notes -join "; ")"
        } else {
            Write-Log "[$version] SETUP ERROR - $($result.Notes -join "; ")" "ERROR"
        }
    }

    # Plugin build and copy section
    $pluginJar = $null
    if (-not $SkipCopy) {
        try {
            if (-not $SkipBuild) {
                Write-Log "Building plugin..."
                $pluginJar = Build-Plugin -Root $projectRoot -GradleTask ":plug-in:shadowJar" -GradleExecutable $GradleExecutable
                Write-Log "Using plugin jar: $pluginJar"
            } else {
                Write-Log "Skipping plugin build, looking for existing jar..."
                $jarCandidate = Get-LatestPluginJar -Root $projectRoot
                if (-not $jarCandidate) {
                    throw "SkipBuild was set, but no plugin jar found in plug-in\build\libs"
                }
                $pluginJar = $jarCandidate.FullName
                Write-Log "SkipBuild enabled; using existing jar: $pluginJar"
            }
        } catch {
            $buildError = $_.Exception.Message
            Write-Log "Plugin build failed: $buildError" "ERROR"
            foreach ($result in $results) {
                if ($result.Status -eq "OK") {
                    $result.Status = "ERROR"
                }
                $result.Notes.Add("plugin build failed: $buildError")
            }
            $SkipCopy = $true
        }
    } else {
        Write-Log "SkipCopy enabled; plugin build skipped."
    }

    if (-not $SkipCopy) {
        Write-Log "Copying plugin to server directories..."
        foreach ($result in $results) {
            if ($result.Status -ne "OK") {
                $result.Notes.Add("copy skipped due to setup error")
                continue
            }

            try {
                $pluginsDir = Join-Path (Join-Path $serversRoot $result.Version) "plugins"
                Ensure-Directory -Path $pluginsDir
                $pluginName = Split-Path $pluginJar -Leaf
                $destination = Join-Path $pluginsDir $pluginName
                Copy-Item -Path $pluginJar -Destination $destination -Force
                $result.Notes.Add("plugin copied")
                Write-Log "[$($result.Version)] plugin copied"
            } catch {
                $result.Status = "ERROR"
                $copyError = $_.Exception.Message
                $result.Notes.Add("plugin copy failed: $copyError")
                Write-Log "[$($result.Version)] ERROR - plugin copy failed: $copyError" "ERROR"
            }
        }
    }

    # Final summary
    Write-Log "==============================================="
    Write-Log "FINAL RESULTS"
    Write-Log "==============================================="

    foreach ($result in $results) {
        if ($result.Status -eq "OK") {
            Write-Log "[$($result.Version)] OK - $($result.Notes -join "; ")"
        } else {
            Write-Log "[$($result.Version)] ERROR - $($result.Notes -join "; ")" "ERROR"
        }
    }

    $okCount = ($results | Where-Object { $_.Status -eq "OK" }).Count
    $errorCount = ($results | Where-Object { $_.Status -ne "OK" }).Count

    Write-Log "==============================================="
    Write-Log "Summary: $okCount OK, $errorCount ERROR"
    Write-Log "Log file: $script:LogFile"
    Write-Log "==============================================="

    if ($errorCount -gt 0) {
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