param(
    [string]$Message = '',
    [switch]$Bump
)

$ErrorActionPreference = 'Stop'

$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

$gradleFile = Join-Path $PSScriptRoot 'app\build.gradle.kts'
$gc = Get-Content $gradleFile -Raw

$mc = [regex]::Match($gc, 'versionCode\s*=\s*(\d+)')
if (-not $mc.Success) { throw "No se pudo leer versionCode de $gradleFile" }
$versionCode = [int]$mc.Groups[1].Value

# Por defecto NO bumpea: publica la version actual (la que se probo con build-debug).
# Usar -Bump solo para release directo sin build-debug previo.
if ($Bump) {
    $versionCode = $versionCode + 1
    $versionName = "1.0.$versionCode"
    $gc = [regex]::Replace($gc, '(versionCode\s*=\s*)\d+', "`${1}$versionCode")
    $gc = [regex]::Replace($gc, '(versionName\s*=\s*")[^"]+(")', "`${1}$versionName`${2}")
    Set-Content $gradleFile $gc -NoNewline
    Write-Host "Bumped to v$versionName"
} else {
    $versionName = "1.0.$versionCode"
    Write-Host "Publishing v$versionName (no bump)"
}

& "$PSScriptRoot\run-tests.ps1"

& "$PSScriptRoot\gradlew.bat" assembleRelease --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) { throw "Gradle assembleRelease failed (exit $LASTEXITCODE)" }

$apkSrc = Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apkSrc)) { throw "No se encontro el APK generado: $apkSrc" }

$releasesDir = Join-Path $PSScriptRoot 'releases'
if (-not (Test-Path $releasesDir)) { New-Item -ItemType Directory -Path $releasesDir | Out-Null }

$apkDst = Join-Path $releasesDir "master-$versionName.apk"
Copy-Item $apkSrc $apkDst -Force

# Conservar los ultimos APKs: permiten comparar firmas ante un install fallido y
# volver a una version previa sin bajarla de GitHub.
Get-ChildItem (Join-Path $releasesDir '*.apk') -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -Skip 3 |
    Remove-Item -Force -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "OK -> releases\master-$versionName.apk"

# Generar update.json para el mecanismo de auto-update (GitHub raw).
$updateJson = Join-Path $PSScriptRoot 'update.json'
$apkUrl = "https://github.com/maurozegarra/master-app/releases/download/v$versionName/master-$versionName.apk"
$json = @{
    versionCode = $versionCode
    versionName = $versionName
    apkUrl = $apkUrl
    changelog = if ($Message -ne '') { $Message } else { '' }
    minVersionCode = 1
} | ConvertTo-Json -Depth 3
Set-Content $updateJson $json -NoNewline
Write-Host "OK -> update.json (v$versionName)"

# Subir APK a GitHub Releases, conservando los KEEP_RELEASES ultimos de version.
$repo = "maurozegarra/master-app"
$tag = "v$versionName"
$KEEP_RELEASES = 3

# Que se borra se decide por lo que el release CONTIENE, no por como se llama: un
# release de version es exactamente un archivo master-X.Y.Z.apk. Con el nombre solo,
# etiquetar contenido como "v2.0.0" bastaria para que el script se lo llevara por
# delante; el repo aloja tambien el release "videos" del que el app descarga los clips
# (TD-062), y borrarlo dejaria videos.json apuntando a urls muertas.
function Test-IsVersionRelease($rel) {
    if ($rel.tagName -notmatch '^v\d+\.\d+\.\d+$') { return $false }
    $names = @($rel.assets | ForEach-Object { $_.name })
    return $names.Count -eq 1 -and $names[0] -match '^master-\d+\.\d+\.\d+\.apk$'
}

$all = gh release list --repo $repo --limit 50 --json tagName 2>$null | ConvertFrom-Json
$versionReleases = @()
foreach ($rel in $all) {
    $full = gh release view $rel.tagName --repo $repo --json tagName,assets,createdAt 2>$null | ConvertFrom-Json
    if ($full -and (Test-IsVersionRelease $full)) {
        $versionReleases += $full
    } else {
        Write-Host "Kept -> $($rel.tagName) (no es un release de version)"
    }
}

# Se conservan los mas recientes para poder RETROCEDER: si una version sale mal, apuntar
# update.json al APK anterior solo es posible si ese APK sigue existiendo.
$versionReleases |
    Where-Object { $_.tagName -ne $tag } |
    Sort-Object { $_.createdAt } -Descending |
    Select-Object -Skip ($KEEP_RELEASES - 1) |
    ForEach-Object {
        gh release delete $_.tagName --repo $repo --yes --cleanup-tag 2>$null
        Write-Host "Deleted old release -> $($_.tagName)"
    }
gh release create $tag $apkDst --repo $repo --title "v$versionName" --notes $Message 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "OK -> GitHub Release $tag uploaded"
} else {
    Write-Host "WARN -> No se pudo subir el release a GitHub (continuando)"
}

if ($Message -ne '') {
    git add -A
    if ($LASTEXITCODE -ne 0) { throw "git add failed (exit $LASTEXITCODE)" }
    git commit -m "$Message (v$versionName)"
    if ($LASTEXITCODE -ne 0) { throw "git commit failed (exit $LASTEXITCODE)" }
    Write-Host "Commit -> $Message (v$versionName)"
    git push origin main 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Push -> origin/main (update.json live)"
    } else {
        Write-Host "WARN -> No se pudo hacer push (update.json no actualizado en GitHub raw)"
    }
}
