---
description: Compilar, instalar en el device y validar una app Android tras un cambio (build + captura + verificacion funcional), y preparar el commit
---

# Validar un cambio en una app Android

Objetivo: tras un cambio, compilar, instalar en el device y verificar en pantalla
que la funcionalidad afectada sigue correcta. Luego preparar el commit.

Convencion: define primero el paquete de la app y el serial del device (cambian por
proyecto/sesion):

```powershell
$pkg = "<tu.paquete>"                 # applicationId de la app
$dev = "<serial-o-ip:puerto>"         # ver workflow connect-phone; o resolver dinamicamente (T3)
```

## Entorno de build (equipo corporativo: Netskope + JFrog)
- **JDK**: JBR de Android Studio -> `JAVA_HOME=C:\Users\mzegarra_ide\Downloads\android-studio\jbr`
  (tambien en `gradle.properties` como `org.gradle.java.home`; su cacerts confia en el CA de Netskope).
- **Gradle 9.4.1** cacheado (no hay wrapper jar; se invoca el `gradle.bat` cacheado).
- **Repos**: JFrog `scp-gradle-public` con Bearer token leido de `~/.npmrc` (plugins.gradle.org y
  repo1 dan 403 detras de Netskope). Configurado en `settings.gradle.kts`.
- **Device**: telefono por ADB (inalambrico o USB); ver workflow `connect-phone`.

## T0 - Compilar
// turbo
```powershell
$env:JAVA_HOME='C:\Users\mzegarra_ide\Downloads\android-studio\jbr'
$gradle=(Resolve-Path "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.4.1-bin\*\gradle-9.4.1\bin\gradle.bat").Path
& $gradle :app:assembleDebug --no-daemon --console=plain *> "$env:TEMP\build.log"
(Get-Content "$env:TEMP\build.log" | Select-String "BUILD SUCCESSFUL|BUILD FAILED|error:|e: ").Line | Select-Object -First 20
```

## T1 - Instalar
// turbo
```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"; $dev="<serial-o-ip:puerto>"
& $adb -s $dev install -r ".\app\build\outputs\apk\debug\app-debug.apk"
```

## T2 - Captura para revisar UI
Nota IMPORTANTE de coordenadas: el screenshot que se visualiza suele venir REESCALADO respecto
a la resolucion real del device. Para `input tap`/`swipe` hay que usar coords REALES del device.
Factor = ancho_real / ancho_screenshot (p. ej. 1080/472 ~= 2.29): un punto visto en (408,377) del
screenshot -> tap real (934,863).
// turbo
```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"; $dev="<serial-o-ip:puerto>"; $pkg="<tu.paquete>"
& $adb -s $dev shell input keyevent KEYCODE_WAKEUP
& $adb -s $dev shell monkey -p $pkg -c android.intent.category.LAUNCHER 1
Start-Sleep 3
& $adb -s $dev shell screencap -p /sdcard/_shot.png
& $adb -s $dev pull /sdcard/_shot.png ".\_shot.png"; & $adb -s $dev shell rm /sdcard/_shot.png
```

## T3 - Verificar la funcionalidad (funcional)
Conceder por shell los permisos que la feature necesite y confirmar el estado esperado
(ventana overlay, servicio en primer plano, valor persistido, etc.). Tecnicas utiles:

- Resolver el serial dinamicamente cuando hay un solo device conectado:
// turbo
```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$dev=(& $adb devices | Select-String "device$" | ForEach-Object { ($_ -split "\s+")[0] } | Select-Object -First 1)
```
- Conceder permisos de runtime/appops para pruebas (ajusta a los que use tu app):
```powershell
& $adb -s $dev shell appops set $pkg SYSTEM_ALERT_WINDOW allow
& $adb -s $dev shell pm grant $pkg android.permission.POST_NOTIFICATIONS
```
- Disparar acciones tocando la UI (`input tap` con coords REALES) en vez de arrancar componentes
  `exported="false"` por shell (un `am start-foreground-service` sobre un servicio no exportado da
  `Permission Denial`). Toca el control desde la app en su lugar.
- Inspeccionar ventanas/estado: `adb shell dumpsys window windows | Select-String "$pkg"`.

## Limpieza
```powershell
Remove-Item -Force -ErrorAction SilentlyContinue .\_shot.png, .\_icon.png
```

## Commit (tras validar)
Sigue la **disciplina de commits definida en el workflow `conventions`** (regla unica): se PROPONE
el commit y se espera confirmacion; commit atomico por cambio; conventional commits. Comando base
(no marcar `// turbo`):
```powershell
git add -A
git commit -m "<tipo>(<scope>): <resumen imperativo>" -m "<detalle opcional>"
```
