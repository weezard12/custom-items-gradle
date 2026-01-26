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
    if (-not [string]::IsNullOrWhiteSpace($RootCandidate)) {
        if (Test-Path $RootCandidate) {
            return (Resolve-Path $RootCandidate).Path
        }
        return [IO.Path]::GetFullPath($RootCandidate)
    }
    return [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
}

function Resolve-OptionalPath {
    param(
        [string]$PathCandidate,
        [string]$BasePath
    )
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
}

function Write-Log {
    param(
        [string]$Message,
        [string]$Level = "INFO"
    )
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $line = "[$timestamp] [$Level] $Message"
    $line | Tee-Object -FilePath $script:LogFile -Append
}

function Ensure-Directory {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        $null = New-Item -Path $Path -ItemType Directory -Force
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
}

function Resolve-JavaHome {
    param([int]$JdkMajor, [string]$Root)
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
}

function Resolve-JavaExe {
    param([int]$JdkMajor, [string]$Root)
    $javaHome = Resolve-JavaHome -JdkMajor $JdkMajor -Root $Root
    if ([string]::IsNullOrWhiteSpace($javaHome)) {
        return $null
    }
    $javaExe = Join-Path $javaHome "bin\java.exe"
    if (Test-Path $javaExe) {
        return $javaExe
    }
    return $null
}

function Ensure-StartBat {
    param(
        [string]$ServerDir,
        [string]$JavaHome,
        [string]$JarName,
        [string]$Xms,
        [string]$Xmx
    )
    $startBat = Join-Path $ServerDir "start.bat"
    $lines = @(
        "@echo off",
        "setlocal",
        ("set `"JAVA_HOME=$JavaHome`""),
        ("`"%JAVA_HOME%\\bin\\java.exe`" -Xms$Xms -Xmx$Xmx -jar `"$JarName`" --nogui")
    )
    Set-Content -Path $startBat -Encoding ASCII -Value $lines
}

function Ensure-EulaAccepted {
    param([string]$ServerDir)
    $eulaPath = Join-Path $ServerDir "eula.txt"
    $stamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $lines = @(
        "# Accepted by test-paper-versions.ps1 on $stamp",
        "eula=true"
    )
    Set-Content -Path $eulaPath -Encoding ASCII -Value $lines
}

function Invoke-Download {
    param(
        [string]$Uri,
        [string]$OutFile
    )
    $params = @{
        Uri     = $Uri
        OutFile = $OutFile
    }
    if ($PSVersionTable.PSVersion.Major -lt 6) {
        $params.UseBasicParsing = $true
    }
    Invoke-WebRequest @params
}

function Add-LogLine {
    param(
        [System.Collections.Generic.List[string]]$Buffer,
        [string]$Line,
        [int]$MaxLines
    )
    if ($Buffer.Count -ge $MaxLines) {
        $Buffer.RemoveAt(0)
    }
    $Buffer.Add($Line)
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

    $stdoutBuffer = New-Object 'System.Collections.Generic.List[string]'
    $stderrBuffer = New-Object 'System.Collections.Generic.List[string]'

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $JavaExe
    $startInfo.Arguments = "-Xms$Xms -Xmx$Xmx -jar `"$JarPath`" --nogui"
    $startInfo.WorkingDirectory = $WorkDir
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo

    $process.add_OutputDataReceived({
        param($sender, $args)
        if ($args.Data) {
            Add-LogLine -Buffer $stdoutBuffer -Line $args.Data -MaxLines $MaxOutputLines
        }
    })
    $process.add_ErrorDataReceived({
        param($sender, $args)
        if ($args.Data) {
            Add-LogLine -Buffer $stderrBuffer -Line $args.Data -MaxLines $MaxOutputLines
        }
    })

    $null = $process.Start()
    $process.BeginOutputReadLine()
    $process.BeginErrorReadLine()

    $exited = $process.WaitForExit($BootWaitSeconds * 1000)

    if (-not $exited -and -not $process.HasExited) {
        try {
            $process.StandardInput.WriteLine("stop")
            $process.StandardInput.Flush()
        } catch {
            # Ignore input failures and rely on timeout/kill.
        }
        $null = $process.WaitForExit($ShutdownWaitSeconds * 1000)
    }

    if (-not $process.HasExited) {
        try {
            $process.Kill()
        } catch {
            # Ignore kill failures.
        }
    }

    $process.WaitForExit()

    return [pscustomobject]@{
        ExitCode = $process.ExitCode
        Stdout   = $stdoutBuffer
        Stderr   = $stderrBuffer
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

    $markerPath = Join-Path $ServerDir ".paper-initialized"
    if (Test-Path $markerPath) {
        Write-Log "Server already initialized: $ServerDir"
        return
    }

    Write-Log "Initial server run (generate files): $ServerDir"
    $firstRun = Invoke-ServerRun -JavaExe $JavaExe -JarPath $JarPath -WorkDir $ServerDir -Xms $Xms -Xmx $Xmx -BootWaitSeconds $BootWaitSeconds -ShutdownWaitSeconds $ShutdownWaitSeconds

    Ensure-EulaAccepted -ServerDir $ServerDir

    Write-Log "Second server run (post-EULA): $ServerDir"
    $secondRun = Invoke-ServerRun -JavaExe $JavaExe -JarPath $JarPath -WorkDir $ServerDir -Xms $Xms -Xmx $Xmx -BootWaitSeconds $BootWaitSeconds -ShutdownWaitSeconds $ShutdownWaitSeconds

    if ($secondRun.ExitCode -ne 0) {
        $tail = ($secondRun.Stderr + $secondRun.Stdout) | Select-Object -Last 10
        $message = "Server run exit code $($secondRun.ExitCode). Tail: $($tail -join ' | ')"
        throw $message
    }

    $stamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Set-Content -Path $markerPath -Encoding ASCII -Value "Initialized on $stamp"
}

function Get-LatestPluginJar {
    param([string]$Root)
    $outputDir = Join-Path $Root "plug-in\build\libs"
    if (-not (Test-Path $outputDir)) {
        return $null
    }
    $jar = Get-ChildItem -Path $outputDir -Filter "*-all.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $jar) {
        $jar = Get-ChildItem -Path $outputDir -Filter "*.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    }
    return $jar
}

function Build-Plugin {
    param(
        [string]$Root,
        [string]$GradleTask,
        [string]$GradleExecutable
    )
    $buildScript = Join-Path $Root "scripts\build-plugin.ps1"
    if (Test-Path $buildScript) {
        Write-Log "Building plugin via scripts\\build-plugin.ps1"
        $buildArgs = @{ ProjectRoot = $Root; GradleTask = $GradleTask }
        if (-not [string]::IsNullOrWhiteSpace($GradleExecutable)) {
            $buildArgs.GradleExecutable = $GradleExecutable
        }
        & $buildScript @buildArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Plugin build failed with exit code $LASTEXITCODE"
        }
    } else {
        throw "Build script not found: $buildScript"
    }

    $jar = Get-LatestPluginJar -Root $Root
    if (-not $jar) {
        throw "Build completed, but no jar found in plug-in\\build\\libs"
    }
    return $jar.FullName
}

$projectRoot = Resolve-ProjectRoot $ProjectRoot

$configPath = if ([string]::IsNullOrWhiteSpace($ConfigFile)) {
    Join-Path $projectRoot "scripts\test-paper-config.json"
} else {
    Resolve-OptionalPath $ConfigFile $projectRoot
}

$config = $null
if ($configPath -and (Test-Path $configPath)) {
    $config = Get-Content -Path $configPath -Raw | ConvertFrom-Json
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

Write-Log "Project root: $projectRoot"
if ($configPath -and (Test-Path $configPath)) {
    Write-Log "Config file: $configPath"
} else {
    Write-Log "Config file: (none)"
}
Write-Log "Versions file: $versionsPath"
Write-Log "Servers root: $serversRoot"

$versionsData = Get-Content -Path $versionsPath -Raw | ConvertFrom-Json
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

Ensure-Directory -Path $serversRoot

$results = New-Object 'System.Collections.Generic.List[object]'

foreach ($entry in $versionEntries) {
    $version = $entry.Name
    $url = $entry.Value
    $status = "OK"
    $notes = New-Object 'System.Collections.Generic.List[string]'

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
                $notes.Add("Jar exists")
            }
        } elseif (-not (Test-Path $jarPath)) {
            throw "SkipDownload set but jar missing: $jarPath"
        }

        $jdkMajor = Get-JdkMajorForVersion -Version $version
        $javaHome = Resolve-JavaHome -JdkMajor $jdkMajor -Root $projectRoot
        if (-not $javaHome) {
            throw "Missing JDK $jdkMajor for $version (set JAVA${jdkMajor}_HOME or place jdks\\jdk$jdkMajor)."
        }
        $javaExe = Join-Path $javaHome "bin\\java.exe"
        if (-not (Test-Path $javaExe)) {
            throw "java.exe not found for JDK $jdkMajor at $javaExe"
        }

        Ensure-StartBat -ServerDir $serverDir -JavaHome $javaHome -JarName "paper.jar" -Xms $Xms -Xmx $Xmx
        $notes.Add("start.bat ready")

        if (-not $SkipServerSetup) {
            Ensure-ServerInitialized -ServerDir $serverDir -JavaExe $javaExe -JarPath $jarPath -Xms $Xms -Xmx $Xmx -BootWaitSeconds $BootWaitSeconds -ShutdownWaitSeconds $ShutdownWaitSeconds
            $notes.Add("initialized")
        } else {
            $notes.Add("server setup skipped")
        }

    } catch {
        $status = "ERROR"
        $notes.Add($_.Exception.Message)
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

$pluginJar = $null
if (-not $SkipCopy) {
    try {
        if (-not $SkipBuild) {
            $pluginJar = Build-Plugin -Root $projectRoot -GradleTask ":plug-in:shadowJar" -GradleExecutable $GradleExecutable
            Write-Log "Using plugin jar: $pluginJar"
        } else {
            $jarCandidate = Get-LatestPluginJar -Root $projectRoot
            if (-not $jarCandidate) {
                throw "SkipBuild was set, but no plugin jar found in plug-in\\build\\libs"
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
            $result.Notes.Add($_.Exception.Message)
            Write-Log "[$($result.Version)] ERROR - plugin copy failed: $($_.Exception.Message)" "ERROR"
        }
    }
}

foreach ($result in $results) {
    if ($result.Status -eq "OK") {
        Write-Log "[$($result.Version)] OK - $($result.Notes -join "; ")"
    } else {
        Write-Log "[$($result.Version)] ERROR - $($result.Notes -join "; ")" "ERROR"
    }
}

$okCount = ($results | Where-Object { $_.Status -eq "OK" }).Count
$errorCount = ($results | Where-Object { $_.Status -ne "OK" }).Count
Write-Log "Completed: $okCount OK, $errorCount ERROR"
Write-Log "Log file: $script:LogFile"

if ($errorCount -gt 0) {
    exit 1
}