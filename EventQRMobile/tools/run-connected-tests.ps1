<#
.SYNOPSIS
    Runs :app:connectedDebugAndroidTest on ONE device, preferring an emulator.

.DESCRIPTION
    connectedDebugAndroidTest fans out to every attached device when Gradle is left to pick, so a personal phone
    plugged in for charging collects (and can hang) the instrumentation run. This wrapper selects a single serial,
    exports ANDROID_SERIAL, and then runs the Gradle task. Any remaining arguments are passed straight to Gradle.

    Selection
      default        the first online serial starting with `emulator-`
      -Serial <id>   that exact serial (must be online), e.g. -Serial M2101K6G
      no emulator    fails with the device list instead of silently using a physical phone; pass -Serial to opt in

.PARAMETER Serial
    adb serial to run on. Overrides the emulator preference.

.EXAMPLE
    pwsh tools/run-connected-tests.ps1
    pwsh tools/run-connected-tests.ps1 -Serial emulator-5554 --tests '*AuthSmokeTest*'
#>
param(
    [string]$Serial = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

# adb is not always on PATH (Android Studio only sets it for its own terminal), so resolve it the same way Gradle does.
function Resolve-Adb {
    $name = if ($env:OS -eq 'Windows_NT') { 'adb.exe' } else { 'adb' }
    $candidates = @()
    foreach ($sdk in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($sdk) { $candidates += (Join-Path (Join-Path $sdk 'platform-tools') $name) }
    }
    $props = Join-Path $root 'local.properties'
    if (Test-Path $props) {
        foreach ($line in [System.IO.File]::ReadAllLines($props)) {
            if ($line -match '^\s*sdk\.dir\s*=\s*(.+)$') {
                # Java properties escape the drive colon and backslashes: C\:/Users/x
                $sdkDir = $Matches[1].Trim() -replace '\\:', ':' -replace '\\\\', '\'
                $candidates += (Join-Path (Join-Path $sdkDir 'platform-tools') $name)
            }
        }
    }
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw 'adb not found: set ANDROID_HOME / ANDROID_SDK_ROOT, or sdk.dir in local.properties, or put adb on PATH.'
}

$adb = Resolve-Adb
$listed = @()
foreach ($line in @(& $adb devices)) {
    # Real adb separates serial and state with a TAB; the "List of devices attached" header does not.
    if ($line -notmatch '\t') { continue }
    $cols = $line -split '\s+'
    $listed += [pscustomobject]@{ Serial = $cols[0]; State = $cols[1] }
}
$online = @($listed | Where-Object { $_.State -eq 'device' })

if ($Serial -ne '') {
    $target = $Serial
    if (-not ($online | Where-Object { $_.Serial -eq $target })) {
        $seen = if ($listed.Count -gt 0) { ($listed | ForEach-Object { "$($_.Serial) ($($_.State))" }) -join ', ' } else { 'none' }
        throw "Device '$target' is not attached and online. Attached: $seen"
    }
} else {
    $emulator = $online | Where-Object { $_.Serial.StartsWith('emulator-') } | Select-Object -First 1
    if (-not $emulator) {
        $seen = if ($listed.Count -gt 0) { ($listed | ForEach-Object { "$($_.Serial) ($($_.State))" }) -join ', ' } else { 'none' }
        throw "No online emulator (serial starting 'emulator-'). Attached: $seen. Start one, or pick a device with -Serial <id>."
    }
    $target = $emulator.Serial
}

$env:ANDROID_SERIAL = $target
Write-Host "connectedDebugAndroidTest -> $target"

Push-Location $root
try {
    & (Join-Path $root 'gradlew.bat') :app:connectedDebugAndroidTest @args
    $code = $LASTEXITCODE
}
finally {
    Pop-Location
}
exit $code
