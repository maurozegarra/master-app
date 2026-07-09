---
description: Convenciones y hallazgos reutilizables para apps Android en este equipo - entorno de build, git, idioma, estilo/tema, iconos y gotchas de device
---

# Convenciones para apps Android (reutilizables)

Lineamientos y hallazgos que aplican de forma transversal a los proyectos Android en
este equipo (no atados a una app concreta). Ajusta paquete, nombres y features al proyecto.

## Entorno de build (equipo corporativo: Netskope + JFrog)
- **JAVA_HOME** = JBR de Android Studio: `C:\Users\mzegarra_ide\Downloads\android-studio\jbr`
  (su cacerts confia en el CA de Netskope). Tambien en `gradle.properties` (`org.gradle.java.home`).
- **Gradle 9.4.1** cacheado en `~/.gradle/wrapper/dists`. No hay wrapper jar: se invoca ese `gradle.bat`.
- **AGP 9.2.x**, Kotlin integrado (built-in). Java/Kotlin target 17.
- **Repos**: solo JFrog virtual `scp-gradle-public` con `Authorization: Bearer <token>` leido de
  `~/.npmrc` (`_authToken=`). plugins.gradle.org / repo1 dan 403 detras de Netskope. Ver `settings.gradle.kts`.
- **SDK**: `%LOCALAPPDATA%\Android\Sdk`; `adb` en `platform-tools\adb.exe`.
- Descargas web: usar `curl.exe -L --ssl-no-revoke` (Invoke-WebRequest suele fallar/bloquearse
  detras de Netskope).

## Git / disciplina de commits
- **PROPONER el commit, NO ejecutarlo por iniciativa propia.** Tras hacer los cambios, presenta el
  mensaje de commit propuesto y ESPERA la confirmacion explicita del usuario antes de correr
  `git commit`. Nada de commits automaticos por cada cambio.
- Commit **atomico** por cambio, tras validar (build OK, idealmente en device). Historico granular
  para poder volver a versiones funcionales (`git reset --hard <hash>` o `git revert`).
- Conventional commits: `feat/fix/refactor/chore/docs/test/style(scope): resumen`.
- Fija la identidad LOCAL del repo si no debe usarse la global corporativa
  (`git config user.name` / `git config user.email`).
- `.gitignore` debe excluir `build/`, `.gradle/`, `local.properties` y capturas temporales (`_*.png`/`_*.svg`).
- Finales de linea: ver workflow `normalize-line-endings`.

## Idioma (LINEAMIENTO)
- **El idioma por defecto de la app es INGLES.** Los strings base van en `res/values/strings.xml`
  en ingles. Las traducciones (si se necesitan) van en `res/values-<lang>/` (p. ej. `values-es`).
  Nunca poner español en `values/` por defecto.
- Nombres propios/marca (`app_name`, wordmark) pueden quedar igual en todos los idiomas
  (`translatable="false"`).

## Titulo / wordmark (LINEAMIENTO)
- El titulo visible suele mostrarse como wordmark con una fuente incrustada (p. ej. **Wallpoet**,
  `res/font/wallpoet.ttf`, `R.font.wallpoet`), NO reutilizando `app_name`.
- En XML: `android:fontFamily="@font/wallpoet"`, `android:textAllCaps="false"`, texto con un string
  dedicado (`translatable="false"`), color `?attr/colorPrimary`.
- Para incrustar la fuente, ver workflow `add-embedded-font`.

## Estilo / tema
- Tema Material3 DayNight NoActionBar con acento propio del proyecto (`colorPrimary`/secundario).
- **Switch estilo One UI** (color ON / gris OFF, thumb blanco): `SwitchCompat` + wrapper propio
  (MaterialSwitch ignora el tamaño; `SwitchCompat` si lo respeta), con drawables y color state lists.
- Tarjetas Material3 Outlined (corner ~16dp, stroke `colorOutlineVariant`, sin elevacion).
- **Icono launcher**: fondo oscuro + glifo tintable con el acento.
- Iconos vectoriales: **reutilizar paths canonicos de Material / extraer del APK del sistema**
  (no dibujar a mano). Ver workflow `add-vector-icon`.

## Device / capturas (gotchas)
- Conexion ADB Wi-Fi: ver workflow `connect-phone`. La IP/puertos CAMBIAN por sesion; PC y telefono
  pueden estar en subredes distintas bajo el mismo SSID (aislamiento de clientes). Fallbacks: USB / hotspot.
- El serial de sesion CAMBIA cada vez. Tras `adb connect` puede reaparecer con un nombre mDNS
  (`adb-<serial>._adb-tls-connect._tcp`) en vez de `IP:puerto`; con un solo device se puede resolver
  dinamicamente: `(& $adb devices | Select-String "device$")[0]`.
- **Coordenadas de `input tap/swipe`**: usar coords REALES del device (p. ej. 1080x2340), NO las del
  screenshot reescalado. Factor = ancho_real / ancho_screenshot.
- Conceder permisos por shell para pruebas: `appops set <pkg> SYSTEM_ALERT_WINDOW allow`,
  `pm grant <pkg> <permiso>` (algunos permisos signature como `WRITE_SECURE_SETTINGS` solo se
  conceden por USB una vez y persisten hasta desinstalar).
- Un componente `exported="false"` NO se puede arrancar desde shell (`am start...` da Permission
  Denial). Para probarlo, dispararlo desde la propia app (tocar un control por `input tap`).
- Abrir el panel de Quick Settings: `adb shell cmd statusbar expand-settings`.
- Leer prefs en build debug: `run-as <pkg> cat shared_prefs/<archivo>.xml`.

## Workflows disponibles
- `validate`: build + install + captura + verificacion funcional + commit.
- `connect-phone`: conectar el device (Wi-Fi/USB/hotspot, subredes distintas).
- `add-vector-icon`, `add-embedded-font`: tecnicas reutilizables de recursos.
- `normalize-line-endings`: uniformar finales de linea (evitar la advertencia CRLF de Git).
