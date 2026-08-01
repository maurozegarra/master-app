---
description: Workflow de versionado, compilación e instalación en dispositivo
---

# Workflow: Versionado, compilación e instalación

1. **Verificar versión**:
   - Leer `versionName` actual en `app/build.gradle.kts` (fuente de verdad).
   - Si los cambios aún no están commiteados, hacer bump (`versionCode += 1`, `versionName = "1.0.<versionCode>"`) e incluirlo en el commit del cambio (ver `/commit`).
   - Si los cambios ya están commiteados con bump, continuar directamente.

2. **Conectar por wireless debugging** (mDNS auto-descubre el puerto):
   ```powershell
   $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
   $dev = (& $adb mdns services | Select-String '_adb-tls-connect' | ForEach-Object { ($_ -split '\s+')[-2] })
   if ($dev) { & $adb connect $dev } else { Write-Host 'No se encontro dispositivo por mDNS' }
   ```

3. **Compilar, instalar y lanzar** la app en el dispositivo:
```powershell
.\build-debug.ps1 -Message "feat: descripción del cambio"
```

Esto corre los tests, compila release (minificado), instala en el device, copia el APK a `/sdcard/Download/`, lanza la app y bumpea `versionCode`.

Si se necesita instalar manualmente sin `build-debug.ps1`:
```powershell
.\gradlew.bat assembleRelease --no-daemon -q
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$dev = (& $adb devices | Select-String "device$")[0].ToString().Split()[0]
$apk = Get-ChildItem .\app\build\outputs\apk\release\*.apk | Select-Object -First 1
& $adb -s $dev install -r $apk.FullName
& $adb -s $dev shell am start -n com.athletic/.MainActivity
```
