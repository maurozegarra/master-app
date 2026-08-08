# Corre los unit tests del proyecto. Falla el script si algun test falla.
# Usado por build-debug.ps1, build-release.ps1 y verify-compile.ps1 como puerta
# de verificacion antes de compilar (Capa 2 del MASTER Forge).

$ErrorActionPreference = 'Stop'

$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

Write-Host "Running unit tests..."
& "$PSScriptRoot\gradlew.bat" test --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) { throw "Unit tests failed (exit $LASTEXITCODE). Fix tests before building." }

Write-Host "OK -> all tests passed"
