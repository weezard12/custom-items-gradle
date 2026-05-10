param(
    [string]$BuildToolsPath,
    [string]$ProjectRoot,
    [string]$WorkDir,
    [string[]]$Versions,
    [string]$JavaExe,
    [switch]$SkipIfPresent
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-ExistingPath {
    param([string[]]$Candidates)
    foreach ($candidate in $Candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }
        if (Test-Path $candidate) {
            return (Resolve-Path $candidate).Path
        }
    }
    return $null
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

function Get-JavaExeForVersion {
    param([string]$Version, [string]$DefaultJava)
    if (-not [string]::IsNullOrWhiteSpace($DefaultJava)) {
        return $DefaultJava
    }

    $parts = $Version.Split('.')
    $versionMajor = [int]$parts[0]
    $versionMinor = if ($parts.Count -gt 1) { [int]$parts[1] } else { 0 }
    if ($versionMajor -ge 26) {
        $jdkMajor = 25
    } elseif ($versionMinor -le 16) {
        $jdkMajor = 8
    } elseif ($versionMinor -eq 17) {
        $jdkMajor = 16
    } elseif ($versionMinor -le 19) {
        $jdkMajor = 17
    } else {
        $jdkMajor = 21
    }

    $envVar = "JAVA${jdkMajor}_HOME"
    $javaHome = [Environment]::GetEnvironmentVariable($envVar, "Process")
    if ([string]::IsNullOrWhiteSpace($javaHome)) {
        $javaHome = [Environment]::GetEnvironmentVariable($envVar, "User")
    }
    if ([string]::IsNullOrWhiteSpace($javaHome)) {
        $javaHome = [Environment]::GetEnvironmentVariable($envVar, "Machine")
    }
    if ([string]::IsNullOrWhiteSpace($javaHome)) {
        $defaultRoot = Join-Path $PSScriptRoot "..\\jdks"
        $candidateHome = Join-Path $defaultRoot ("jdk$jdkMajor")
        if (Test-Path $candidateHome) {
            $javaHome = $candidateHome
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($javaHome)) {
        $candidate = Join-Path $javaHome "bin\\java.exe"
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    Write-Warning "JAVA${jdkMajor}_HOME not set or invalid; using 'java' from PATH."
    return "java"
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

$projectRootResolved = Resolve-ExistingPath @(
    $ProjectRoot,
    (Join-Path $PSScriptRoot "..")
)
if (-not $projectRootResolved) {
    throw "Can't resolve project root."
}

$buildToolsResolved = Resolve-ExistingPath @(
    $BuildToolsPath,
    (Join-Path $projectRootResolved "BuildTools.jar"),
    (Join-Path $projectRootResolved "BuildTools.exe")
)
if (-not $buildToolsResolved) {
    throw "BuildTools not found. Provide -BuildToolsPath or place BuildTools.jar in the project root."
}

$workDirResolved = Resolve-ExistingPath @(
    $WorkDir,
    $projectRootResolved
)
if (-not $workDirResolved) {
    throw "Can't resolve work directory."
}

if (-not $Versions -or $Versions.Count -eq 0) {
    $Versions = Get-RequiredVersionsFromGradle (Join-Path $projectRootResolved "build.gradle")
}

if (-not $Versions -or $Versions.Count -eq 0) {
    $Versions = @(
        "1.12.2",
        "1.13.2",
        "1.14.4",
        "1.15.2",
        "1.16.5",
        "1.16.4",
        "1.16.3",
        "1.16.2",
        "1.16.1",
        "1.16",
        "1.17.1",
        "1.18.2",
        "1.19.4",
        "1.19.3",
        "1.19.2",
        "1.19.1",
        "1.19",
        "1.20.6",
        "1.20.5",
        "1.20.4",
        "1.20.3",
        "1.20.2",
        "1.20.1",
        "1.20",
        "1.21.11",
        "1.21.10",
        "1.21.9",
        "1.21.8",
        "1.21.7",
        "1.21.6",
        "1.21.5",
        "1.21.4",
        "1.21.3",
        "1.21.2",
        "1.21.1",
        "1.21"
    )
}

Write-Host "BuildTools: $buildToolsResolved"
Write-Host "Working dir: $workDirResolved"
Write-Host "Versions: $($Versions -join ', ')"

Push-Location $workDirResolved
try {
    foreach ($version in $Versions) {
        if ([int]$version.Split('.')[0] -ge 26) {
            Write-Host "Skipping BuildTools for $version (26.x uses the remote Paper API in this build)."
            continue
        }

        if ($SkipIfPresent -and (Test-VersionInstalled $version)) {
            Write-Host "Skipping $version (already installed)"
            continue
        }

        $javaForVersion = Get-JavaExeForVersion $version $JavaExe
        Write-Host "Building Spigot $version using $javaForVersion"

        if ($buildToolsResolved.ToLower().EndsWith(".jar")) {
            & $javaForVersion -jar $buildToolsResolved --rev $version
        } else {
            & $buildToolsResolved --rev $version
        }

        if ($LASTEXITCODE -ne 0) {
            throw "BuildTools failed for $version with exit code $LASTEXITCODE"
        }
    }
} finally {
    Pop-Location
}
