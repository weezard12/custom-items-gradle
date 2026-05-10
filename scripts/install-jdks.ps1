param(
    [string]$InstallRoot,
    [int[]]$JdkVersions,
    [string]$Architecture,
    [switch]$Persist,
    [switch]$SkipIfPresent,
    [switch]$Overwrite
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-InstallRoot {
    param([string]$RootCandidate)
    if (-not [string]::IsNullOrWhiteSpace($RootCandidate)) {
        if (Test-Path $RootCandidate) {
            return (Resolve-Path $RootCandidate).Path
        }
        return [IO.Path]::GetFullPath($RootCandidate)
    }
    $defaultRoot = Join-Path $PSScriptRoot "..\\jdks"
    return [IO.Path]::GetFullPath($defaultRoot)
}

function Resolve-Architecture {
    param([string]$Arch)
    if (-not [string]::IsNullOrWhiteSpace($Arch)) {
        return $Arch.ToLower()
    }
    $processorArch = $env:PROCESSOR_ARCHITECTURE
    if ($processorArch -eq "ARM64") {
        return "aarch64"
    }
    return "x64"
}

function Get-DefaultJdkVersions {
    return @(8, 16, 17, 21, 25)
}

function Get-AdoptiumUrl {
    param([int]$JdkVersion, [string]$Arch)
    return "https://api.adoptium.net/v3/binary/latest/$JdkVersion/ga/windows/$Arch/jdk/hotspot/normal/adoptium"
}

function Ensure-Directory {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        $null = New-Item -ItemType Directory -Path $Path -Force
    }
}

function Install-Jdk {
    param([int]$JdkVersion, [string]$Arch, [string]$Root, [switch]$Skip, [switch]$OverwriteExisting)

    $destination = Join-Path $Root ("jdk$JdkVersion")
    if (Test-Path $destination) {
        if ($Skip) {
            Write-Host "Skipping JDK $JdkVersion (already installed at $destination)"
            return $destination
        }
        if (-not $OverwriteExisting) {
            throw "JDK $JdkVersion already exists at $destination. Use -SkipIfPresent or -Overwrite."
        }
        Remove-Item -Recurse -Force $destination
    }

    $tempDir = Join-Path $Root ("_tmp-jdk$JdkVersion-" + [Guid]::NewGuid().ToString("N"))
    Ensure-Directory $tempDir
    $zipPath = Join-Path $tempDir ("jdk$JdkVersion.zip")

    $url = Get-AdoptiumUrl $JdkVersion $Arch
    Write-Host "Downloading JDK $JdkVersion ($Arch) from $url"
    Invoke-WebRequest -Uri $url -OutFile $zipPath

    Write-Host "Extracting JDK $JdkVersion"
    Expand-Archive -Path $zipPath -DestinationPath $tempDir

    $extractedDirs = @(Get-ChildItem -Path $tempDir -Directory)
    if ($extractedDirs.Count -eq 0) {
        throw "Unexpected archive layout for JDK $JdkVersion."
    }
    if ($extractedDirs.Count -gt 1) {
        $first = $extractedDirs | Select-Object -First 1
        Write-Host "Multiple directories found in archive. Using $($first.Name)."
        $extractedDir = $first
    } else {
        $extractedDir = $extractedDirs[0]
    }

    Move-Item -Path $extractedDir.FullName -Destination $destination
    Remove-Item -Recurse -Force $tempDir

    return $destination
}

function Set-JavaHome {
    param([int]$JdkVersion, [string]$Path, [switch]$PersistVar)

    $varName = "JAVA$JdkVersion`_HOME"
    Set-Item -Path "Env:$varName" -Value $Path
    if ($PersistVar) {
        [Environment]::SetEnvironmentVariable($varName, $Path, "User")
    }
}

$installRoot = Resolve-InstallRoot $InstallRoot
Ensure-Directory $installRoot

$arch = Resolve-Architecture $Architecture
if (-not $JdkVersions -or $JdkVersions.Count -eq 0) {
    $JdkVersions = Get-DefaultJdkVersions
}

Write-Host "Install root: $installRoot"
Write-Host "Architecture: $arch"
Write-Host "JDK versions: $($JdkVersions -join ', ')"

foreach ($jdkVersion in $JdkVersions) {
    $path = Install-Jdk $jdkVersion $arch $installRoot $SkipIfPresent $Overwrite
    Set-JavaHome $jdkVersion $path $Persist
    Write-Host "JAVA$jdkVersion`_HOME = $path"
}

Write-Host "Done."
if (-not $Persist) {
    Write-Host "Note: JAVA*_HOME variables are only set for this PowerShell session."
    Write-Host "Use -Persist to set them for your user profile."
}
