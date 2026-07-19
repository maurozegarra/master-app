param(
    [string]$Message = ''
)

$ErrorActionPreference = 'Stop'

$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

$gradleFile = Join-Path $PSScriptRoot 'app\build.gradle.kts'
$gc = Get-Content $gradleFile -Raw

$mc = [regex]::Match($gc, 'versionCode\s*=\s*(\d+)')
if (-not $mc.Success) { throw "No se pudo leer versionCode de $gradleFile" }
$versionCode = [int]$mc.Groups[1].Value

$mv = [regex]::Match($gc, 'versionName\s*=\s*"([^"]+)"')
if (-not $mv.Success) { throw "No se pudo leer versionName de $gradleFile" }
$versionName = $mv.Groups[1].Value

Write-Host "Building v$versionName (code $versionCode)..."

& "$PSScriptRoot\gradlew.bat" assembleRelease --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) { throw "Gradle assembleRelease failed (exit $LASTEXITCODE)" }

$apkSrc = Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apkSrc)) { throw "No se encontro el APK generado: $apkSrc" }

# Copia a releases/
$releasesDir = Join-Path $PSScriptRoot 'releases'
if (-not (Test-Path $releasesDir)) { New-Item -ItemType Directory -Path $releasesDir | Out-Null }
Remove-Item (Join-Path $releasesDir 'athletic-*.apk') -ErrorAction SilentlyContinue
$apkDst = Join-Path $releasesDir "athletic-$versionName.apk"
Copy-Item $apkSrc $apkDst -Force
Write-Host "OK -> releases\athletic-$versionName.apk"

# Instalar en el device
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$devices = & $adb devices
$deviceLine = ($devices | Select-String "\b\d+\.\d+\.\d+\.\d+:\d+\b").Matches.Value
if ($deviceLine) {
    Write-Host "Installing on $deviceLine..."
    & $adb -s $deviceLine install -r $apkSrc
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Install failed, trying uninstall + install..."
        & $adb -s $deviceLine uninstall com.athletic
        & $adb -s $deviceLine install $apkSrc
    }
    & $adb -s $deviceLine shell am force-stop com.athletic
    & $adb -s $deviceLine shell am start -n com.athletic/.MainActivity

    # Copiar APK a la carpeta Download del telefono
    & $adb -s $deviceLine push $apkSrc /sdcard/Download/athletic-$versionName.apk
    Write-Host "OK -> Copied to Download/athletic-$versionName.apk on device"

    Write-Host "OK -> Installed and launched on $deviceLine"
} else {
    Write-Host "WARN -> No device connected via adb"
}

# Bump versionCode
$newVersionCode = $versionCode + 1
$newVersionName = "1.0.$newVersionCode"
$gc = [regex]::Replace($gc, '(versionCode\s*=\s*)\d+', "`${1}$newVersionCode")
$gc = [regex]::Replace($gc, '(versionName\s*=\s*")[^"]+(")', "`${1}$newVersionName`${2}")
Set-Content $gradleFile $gc -NoNewline

Write-Host "Bumped -> $newVersionName"
