param(
    [string]$MavenRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-MavenRoot {
    param([string]$RootCandidate)
    if (-not [string]::IsNullOrWhiteSpace($RootCandidate)) {
        if (Test-Path $RootCandidate) {
            return (Resolve-Path $RootCandidate).Path
        }
        return [IO.Path]::GetFullPath($RootCandidate)
    }
    $defaultRoot = Join-Path $env:USERPROFILE ".m2\repository"
    return [IO.Path]::GetFullPath($defaultRoot)
}

function Get-InstalledVersions {
    param([string]$Root, [string]$GroupPath)
    $path = Join-Path $Root $GroupPath
    if (-not (Test-Path $path)) {
        return @()
    }
    $versions = Get-ChildItem -Path $path -Directory | Select-Object -ExpandProperty Name | Sort-Object
    return @($versions)
}

$mavenRoot = Resolve-MavenRoot $MavenRoot

# Wrap each call with @() to ensure array results
$spigot = @(Get-InstalledVersions $mavenRoot "org\spigotmc\spigot")
$craftbukkit = @(Get-InstalledVersions $mavenRoot "org\bukkit\craftbukkit")
$bukkit = @(Get-InstalledVersions $mavenRoot "org\bukkit\bukkit")

Write-Host "Maven root: $mavenRoot"
Write-Host "org.spigotmc:spigot versions:"
if ($spigot.Count -gt 0) {
    $spigot | ForEach-Object { Write-Host "  $_" }
} else {
    Write-Host "  (none found)"
}

Write-Host "org.bukkit:craftbukkit versions:"
if ($craftbukkit.Count -gt 0) {
    $craftbukkit | ForEach-Object { Write-Host "  $_" }
} else {
    Write-Host "  (none found)"
}

Write-Host "org.bukkit:bukkit versions:"
if ($bukkit.Count -gt 0) {
    $bukkit | ForEach-Object { Write-Host "  $_" }
} else {
    Write-Host "  (none found)"
}