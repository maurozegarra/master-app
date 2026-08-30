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

# Bump ANTES de compilar: el APK lleva la nueva version, y el bump va dentro
# del commit del cambio que genera el APK (ver .windsurf/workflows/commit.md).
$newVersionCode = $versionCode + 1
$newVersionName = "1.0.$newVersionCode"
$gc = [regex]::Replace($gc, '(versionCode\s*=\s*)\d+', "`${1}$newVersionCode")
$gc = [regex]::Replace($gc, '(versionName\s*=\s*")[^"]+(")', "`${1}$newVersionName`${2}")
Set-Content $gradleFile $gc -NoNewline

Write-Host "Building v$newVersionName (code $newVersionCode)..."

& "$PSScriptRoot\run-tests.ps1"

& "$PSScriptRoot\gradlew.bat" assembleRelease --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) { throw "Gradle assembleRelease failed (exit $LASTEXITCODE)" }

$apkSrc = Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apkSrc)) { throw "No se encontro el APK generado: $apkSrc" }

# Copia a releases/
$releasesDir = Join-Path $PSScriptRoot 'releases'
if (-not (Test-Path $releasesDir)) { New-Item -ItemType Directory -Path $releasesDir | Out-Null }
Remove-Item (Join-Path $releasesDir 'master-*.apk') -ErrorAction SilentlyContinue
$apkDst = Join-Path $releasesDir "master-$newVersionName.apk"
Copy-Item $apkSrc $apkDst -Force
Write-Host "OK -> releases\master-$newVersionName.apk"

# Instalar en el device
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$devices = & $adb devices
$deviceLines = ($devices | Select-String "\bdevice$").Line -replace "\s+device$", ""
# Si hay multiples dispositivos (IP + mDNS del mismo telefono), preferir el que es IP:puerto
$deviceLine = ($deviceLines | Where-Object { $_ -match '^\d+\.\d+\.\d+\.\d+:\d+$' } | Select-Object -First 1)
if (-not $deviceLine) { $deviceLine = ($deviceLines | Select-Object -First 1) }
if ($deviceLine) {
    Write-Host "Installing on $deviceLine..."
    $installOut = & $adb -s $deviceLine install -r $apkSrc 2>&1
    $installOut | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) {
        # NUNCA desinstalar automaticamente. `adb uninstall` borra /data/data del paquete,
        # y ahi viven trainings e historial (SharedPreferences). El 29-ago-2026 el fallback
        # automatico que habia aqui destruyo el historial del usuario: la PC nueva firmaba
        # con otro debug.keystore, el install fallo por firma y el script desinstalo solo.
        # La decision de borrar datos es del usuario, no del script.
        if ($installOut -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match') {
            Write-Host ""
            Write-Host "La firma del APK no coincide con la del app ya instalado." -ForegroundColor Yellow
            Write-Host "Causa tipica: el release se firma con ~/.android/debug.keystore, que es" -ForegroundColor Yellow
            Write-Host "por maquina. Si cambiaste de PC, copia el debug.keystore de la anterior." -ForegroundColor Yellow
            Write-Host ""
            Write-Host "Comparar huellas:" -ForegroundColor Yellow
            Write-Host "  keytool -list -v -keystore `$env:USERPROFILE\.android\debug.keystore -storepass android -alias androiddebugkey" -ForegroundColor Yellow
            Write-Host "  apksigner verify --print-certs <apk-instalado>" -ForegroundColor Yellow
            Write-Host ""
            Write-Host "Si aun asi decides desinstalar, EXPORTA TUS DATOS PRIMERO. El comando" -ForegroundColor Yellow
            Write-Host "borra trainings e historial de forma irreversible:" -ForegroundColor Yellow
            Write-Host "  adb -s $deviceLine uninstall com.maurozegarra.master" -ForegroundColor Yellow
            Write-Host ""
        }
        throw "Install fallo (exit $LASTEXITCODE). El APK quedo en $apkDst; no se desinstalo nada."
    }
    & $adb -s $deviceLine shell am force-stop com.maurozegarra.master
    & $adb -s $deviceLine shell am start -n com.maurozegarra.master/.MainActivity

    # Copiar APK a la carpeta Download del telefono
    & $adb -s $deviceLine push $apkSrc /sdcard/Download/master-$newVersionName.apk
    Write-Host "OK -> Copied to Download/master-$newVersionName.apk on device"

    Write-Host "OK -> Installed and launched on $deviceLine"
} else {
    Write-Host "WARN -> No device connected via adb"
}
