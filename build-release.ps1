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

& "$PSScriptRoot\gradlew.bat" assembleRelease --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) { throw "Gradle assembleRelease failed (exit $LASTEXITCODE)" }

$apkSrc = Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apkSrc)) { throw "No se encontro el APK generado: $apkSrc" }

$releasesDir = Join-Path $PSScriptRoot 'releases'
if (-not (Test-Path $releasesDir)) { New-Item -ItemType Directory -Path $releasesDir | Out-Null }
Remove-Item (Join-Path $releasesDir '*.apk') -ErrorAction SilentlyContinue

$apkDst = Join-Path $releasesDir "athletic-$versionName.apk"
Copy-Item $apkSrc $apkDst -Force

Write-Host ""
Write-Host "OK -> releases\athletic-$versionName.apk"

$newVersionCode = $versionCode + 1
$newVersionName = "1.0.$newVersionCode"
$gc = [regex]::Replace($gc, '(versionCode\s*=\s*)\d+', "`${1}$newVersionCode")
$gc = [regex]::Replace($gc, '(versionName\s*=\s*")[^"]+(")', "`${1}$newVersionName`${2}")
Set-Content $gradleFile $gc -NoNewline

Write-Host "Bumped -> $newVersionName"

if ($Message -ne '') {
    git add -A
    if ($LASTEXITCODE -ne 0) { throw "git add failed (exit $LASTEXITCODE)" }
    git commit -m "$Message (v$versionName)"
    if ($LASTEXITCODE -ne 0) { throw "git commit failed (exit $LASTEXITCODE)" }
    Write-Host "Commit -> $Message (v$versionName)"
}
