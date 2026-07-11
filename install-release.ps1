param(
    [string]$Device = 'all'
)

$ErrorActionPreference = 'Continue'

$adb = 'C:\Users\MASTER\AppData\Local\Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) {
    $adb = (Get-Command adb -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Path)
    if (-not $adb) { throw "adb no encontrado" }
}

$apk = (Get-ChildItem -Path (Join-Path $PSScriptRoot 'releases') -Filter 'athletic-*.apk' |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1).FullName
if (-not $apk) { throw "No hay APK en releases/" }

$apkName = Split-Path $apk -Leaf

$targets = @()
if ($Device -eq 'all' -or $Device -eq 'samsung') { $targets += 'SM_S948B' }
if ($Device -eq 'all' -or $Device -eq 'xiaomi') { $targets += '23013PC75G' }

$lines = & $adb devices -l 2>&1
$matched = $lines | Where-Object { $_ -match '^\s*(\S+)\s+device.*\s+model:(\S+)' } |
    ForEach-Object {
        $serial = $Matches[1]
        $model = $Matches[2]
        if ($model -in $targets) { [PSCustomObject]@{ Serial = $serial; Model = $model } }
    } |
    Sort-Object Serial -Unique

if (-not $matched) { throw "No se encontraron dispositivos para $Device" }

foreach ($dev in $matched) {
    Write-Host ""
    Write-Host "Device -> $($dev.Model) ($($dev.Serial))"

    & $adb -s $dev.Serial install -r $apk
    if ($LASTEXITCODE -eq 0) { Write-Host "  Install OK" } else { Write-Host "  Install FAIL (exit $LASTEXITCODE)" }

    & $adb -s $dev.Serial push $apk "/sdcard/Download/$apkName"
    if ($LASTEXITCODE -eq 0) { Write-Host "  Download copy OK" } else { Write-Host "  Download copy FAIL (exit $LASTEXITCODE)" }
}
