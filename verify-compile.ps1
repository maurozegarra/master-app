# Verifica que el proyecto COMPILA (sin generar APK, sin subir version, sin commit).
# Usa el wrapper (gradlew) -> Gradle 9.4.1.
$ErrorActionPreference = 'Stop'

$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

& "$PSScriptRoot\gradlew.bat" compileReleaseKotlin --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) { throw "Gradle compileReleaseKotlin failed (exit $LASTEXITCODE)" }

Write-Host ""
Write-Host "OK -> compila sin errores"
