param(
    [string]$ProjectRoot,
    [string]$GradleTask = ":plug-in:shadowJar",
    [string]$GradleExecutable,
    [string]$OnlyVersions,
    [switch]$IncludeEventHandler,
    [switch]$IncludeTests,
    [switch]$SkipJdkCheck,
    [switch]$SkipDependencyCheck
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

function Get-RequiredVersionsFromGradle {
    param([string]$GradleFile)
    if (-not (Test-Path $GradleFile)) {
        return @()
    }
    $content = Get-Content -Path $GradleFile -Raw
    $pattern = '(?:org\.spigotmc:spigot|org\.spigotmc:spigot-api|org\.bukkit:bukkit|org\.bukkit:craftbukkit):([0-9.]+)-R0\.1-SNAPSHOT'
    $matches = [regex]::Matches($content, $pattern)
    $ordered = New-Object 'System.Collections.Specialized.OrderedDictionary'
    foreach ($match in $matches) {
        $version = $match.Groups[1].Value
        if (-not $ordered.Contains($version)) {
            $null = $ordered.Add($version, $true)
        }
    }
    return @($ordered.Keys)
}

function Test-VersionInstalled {
    param([string]$Version)
    $m2 = Join-Path $env:USERPROFILE ".m2\\repository"
    $candidates = @(
        Join-Path $m2 "org\\spigotmc\\spigot\\$Version-R0.1-SNAPSHOT\\spigot-$Version-R0.1-SNAPSHOT.jar",
        Join-Path $m2 "org\\bukkit\\craftbukkit\\$Version-R0.1-SNAPSHOT\\craftbukkit-$Version-R0.1-SNAPSHOT.jar",
        Join-Path $m2 "org\\bukkit\\bukkit\\$Version-R0.1-SNAPSHOT\\bukkit-$Version-R0.1-SNAPSHOT.jar"
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $true
        }
    }
    return $false
}

function Get-RequiredJdkMajors {
    param([string[]]$Versions)
    $majors = New-Object 'System.Collections.Generic.HashSet[int]'
    foreach ($version in $Versions) {
        $mcMajor = [int]$version.Split('.')[1]
        if ($mcMajor -le 16) {
            $null = $majors.Add(8)
        } elseif ($mcMajor -eq 17) {
            $null = $majors.Add(16)
        } elseif ($mcMajor -le 19) {
            $null = $majors.Add(17)
        } else {
            $null = $majors.Add(21)
        }
    }
    return @($majors.ToArray() | Sort-Object)
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
        $candidateHome = Join-Path $defaultRoot ("jdk$JdkMajor")
        if (Test-Path $candidateHome) {
            $javaHome = $candidateHome
        }
    }
    if ([string]::IsNullOrWhiteSpace($javaHome)) {
        return $null
    }
    $javaExe = Join-Path $javaHome "bin\\java.exe"
    if (Test-Path $javaExe) {
        return $javaHome
    }
    return $null
}

$projectRoot = Resolve-ProjectRoot $ProjectRoot
$gradleFile = Join-Path $projectRoot "build.gradle"
if (-not [string]::IsNullOrWhiteSpace($OnlyVersions)) {
    $versions = $OnlyVersions.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ }
} else {
    $versions = Get-RequiredVersionsFromGradle $gradleFile
    if (-not $versions -or $versions.Count -eq 0) {
        throw "No required Minecraft versions found in build.gradle."
    }
}

$requiredBaseVersions = @("1.12.2", "1.16.5")
foreach ($baseVersion in $requiredBaseVersions) {
    if (-not ($versions -contains $baseVersion)) {
        $versions += $baseVersion
    }
}

if ($IncludeEventHandler -and -not ($versions -contains "1.12.2")) {
    $versions += "1.12.2"
}

$ready = $true
$missingJdks = @()
$missingVersions = @()

if (-not $SkipJdkCheck) {
    $requiredJdks = Get-RequiredJdkMajors $versions
    foreach ($jdkMajor in $requiredJdks) {
        if (-not (Resolve-JavaHome $jdkMajor $projectRoot)) {
            $missingJdks += $jdkMajor
        }
    }
    if ($missingJdks.Count -gt 0) {
        $ready = $false
        Write-Host "Missing JDKs: $($missingJdks -join ', ')"
        Write-Host "Run: powershell -ExecutionPolicy Bypass -File scripts\\install-jdks.ps1 -Persist"
    }
}

if (-not $SkipDependencyCheck) {
    foreach ($version in $versions) {
        if (-not (Test-VersionInstalled $version)) {
            $missingVersions += $version
        }
    }
    if ($missingVersions.Count -gt 0) {
        $ready = $false
        Write-Host "Missing Spigot/CraftBukkit artifacts for: $($missingVersions -join ', ')"
        Write-Host "Run: powershell -ExecutionPolicy Bypass -File scripts\\install-spigot.ps1"
        $buildToolsJar = Join-Path $projectRoot "BuildTools.jar"
        $buildToolsExe = Join-Path $projectRoot "BuildTools.exe"
        if (-not (Test-Path $buildToolsJar) -and -not (Test-Path $buildToolsExe)) {
            Write-Host "BuildTools not found in repo root. Download it from https://www.spigotmc.org/wiki/buildtools/"
        }
    }
}

if (-not $ready) {
    exit 1
}

if ([string]::IsNullOrWhiteSpace($GradleExecutable)) {
    $candidateBat = Join-Path $projectRoot "gradlew.bat"
    $candidateSh = Join-Path $projectRoot "gradlew"
    if (Test-Path $candidateBat) {
        $GradleExecutable = $candidateBat
    } elseif (Test-Path $candidateSh) {
        $GradleExecutable = $candidateSh
    } else {
        throw "Gradle wrapper not found in $projectRoot."
    }
}

Push-Location $projectRoot
try {
    $gradleArgs = @($GradleTask)
    if (-not [string]::IsNullOrWhiteSpace($OnlyVersions)) {
        $gradleArgs += "-PonlyVersions=$OnlyVersions"
    }
    if ($IncludeEventHandler) {
        $gradleArgs += "-PincludeEventHandler=true"
    }
    if ($IncludeTests) {
        $gradleArgs += "-PincludeTests=true"
    }

    Write-Host "Building with: $GradleExecutable $($gradleArgs -join ' ')"
    & $GradleExecutable @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$outputDir = Join-Path $projectRoot "plug-in\\build\\libs"
if (Test-Path $outputDir) {
    $jar = Get-ChildItem -Path $outputDir -Filter "*-all.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $jar) {
        $jar = Get-ChildItem -Path $outputDir -Filter "*.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    }
    if ($jar) {
        Write-Host "Built jar: $($jar.FullName)"
    } else {
        Write-Host "Build completed, but no jar found in $outputDir"
    }
} else {
    Write-Host "Build completed, but output directory not found: $outputDir"
}
