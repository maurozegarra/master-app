---
description: Compilar un proyecto Android desde CLI — entorno, scripts y tres modos (verificar, debug, release) reutilizables en cualquier proyecto
---

# Compilar un proyecto Android desde CLI

Guia autocontenida del proceso de build. Pensada para replicarse en otro proyecto
Android con Gradle + Kotlin + Compose.

## Entorno de build

- **JDK 21** (JBR de Android Studio): `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`.
  Tambien fijado en `gradle.properties` (`org.gradle.java.home`).
- **Gradle 9.4.1** via wrapper (`gradlew.bat` + `gradle/wrapper/gradle-wrapper.properties`).
  El wrapper descarga Gradle automaticamente si no esta en cache.
- **AGP 9.2.1**, **Kotlin 2.2.10** (declarados en `build.gradle.kts` del root).
- **Java/Kotlin target**: 17 (aunque el JDK es 21).
- **Android SDK**: `%LOCALAPPDATA%\Android\Sdk`. Ruta en `local.properties` (`sdk.dir`).
- **Repos**: `google()` + `mavenCentral()` en `settings.gradle.kts`. Si hay proxy
  corporativo (Netskope), usar JFrog con Bearer token de `~/.npmrc` (ver `conventions.md`).
- Flag `--no-daemon` para evitar procesos background de Gradle.

## Archivos minimos para replicar el build

```
project/
├── gradle.properties              # JAVA_HOME, memoria JVM, flags AndroidX/Kotlin
├── gradlew / gradlew.bat          # Wrapper script
├── gradle/
│   ├── wrapper/
│   │   └── gradle-wrapper.properties   # distributionUrl = gradle-9.4.1-bin.zip
│   └── gradle-daemon-jvm.properties    # toolchainVersion = 21
├── settings.gradle.kts            # Repositorios + nombre del proyecto + include(":app")
├── build.gradle.kts               # Versiones de plugins (AGP, Kotlin)
├── local.properties               # sdk.dir (NO se versiona)
└── app/
    └── build.gradle.kts           # SDK, applicationId, dependencies, buildTypes, signing
```

### Contenido de referencia

**`gradle.properties`**:
```properties
org.gradle.jvmargs=-Xmx1024m -XX:+UseSerialGC -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8
kotlin.daemon.jvmargs=-Xmx768m -XX:+UseSerialGC
org.gradle.workers.max=2
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
org.gradle.java.home=C:/Program Files/Android/Android Studio/jbr
```

**`gradle/wrapper/gradle-wrapper.properties`**:
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.4.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

**`settings.gradle.kts`** (sin proxy corporativo):
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "<NombreProyecto>"
include(":app")
```

**`build.gradle.kts`** (root):
```kotlin
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
```

**`app/build.gradle.kts`** (esqueleto):
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tu.paquete"
    compileSdk = 36
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.tu.paquete"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Firmar release con clave debug para firma estable (permite updates sin desinstalar)
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

## Conectar el telefono (ADB)

Objetivo: dejar el telefono visible en `adb devices` como `device` para instalar
el APK, correr tests y tomar capturas. Cubre 4 escenarios.

### Requisitos en el telefono
1. **Ajustes -> Opciones de desarrollador -> Depuracion inalambrica: ON** (o "Depuracion USB" si
   usas cable).
2. La pantalla de Depuracion inalambrica muestra **"Direccion IP y puerto"** (puerto de CONEXION) y
   la opcion **"Vincular dispositivo con codigo de vinculacion"** (puerto de VINCULACION, distinto).

### Escenario A - Wi-Fi, PC y telefono en la misma subred
// turbo
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb connect 192.168.x.x:PUERTO_CONNECT
& $adb devices
```
Si pide autorizacion (host nuevo), vincular primero:
```powershell
& $adb pair 192.168.x.x:PUERTO_PAIR   # IP:PUERTO_PAIR y codigo de "Vincular dispositivo"
```

### Escenario B - Subredes distintas bajo el mismo SSID
Sintoma: PC y telefono muestran IPs de subredes diferentes aunque el SSID sea el mismo
(aislamiento de clientes / VLANs, comun en redes corporativas).
1. Diagnostico de alcance:
// turbo
```powershell
ping -n 3 <IP-del-telefono>
```
2. Si el ping RESPONDE: intentar ADB Wi-Fi directo (Escenario A). Si `connect` falla pese al ping,
   el puerto TCP esta bloqueado por firewall/VLAN -> usar Escenario C o D.
3. Si el ping NO responde: no hay ruta directa -> Escenario C (USB) o D (hotspot).

### Escenario C - USB (fallback mas simple y robusto)
1. Conecta el cable, acepta **"Permitir depuracion USB"** en el telefono.
// turbo
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
```
Esperado: una linea con `device`. Ese serial se pasa a los comandos con `-s <serial>`.

### Escenario D - Hotspot para forzar la misma subred
1. Enciende el **hotspot del telefono** y conecta la **PC** a ese hotspot (o al reves).
2. Sigue el Escenario A con la nueva IP del telefono.

### Verificacion final
// turbo
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices
& $adb -s <serial-o-ip:puerto> get-state   # debe imprimir: device
```

### Capturas de pantalla (evitar PNG corrupto)
**NUNCA** captures con `>` de PowerShell (corrompe el binario). Usar `screencap` + `pull`:
// turbo
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$dev = "<ip:puerto o serial>"
& $adb -s $dev shell screencap -p /sdcard/_shot.png
& $adb -s $dev pull /sdcard/_shot.png .\_shot.png
& $adb -s $dev shell rm /sdcard/_shot.png
```

### Troubleshooting
- **IP y puertos cambian** (DHCP + puertos aleatorios): tomar siempre los valores actuales de la
  pantalla de Depuracion inalambrica; `PUERTO_PAIR` != `PUERTO_CONNECT`.
- **`failed to authenticate`**: host no vinculado -> `adb pair`.
- **`connection refused` / timeout con ping OK**: puerto TCP bloqueado por VLAN/firewall -> USB o hotspot.
- Reiniciar el daemon si algo queda raro:
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb kill-server; & $adb start-server
```

## T0 - Verificar que compila (sin generar APK)

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
& "$PSScriptRoot\gradlew.bat" compileReleaseKotlin --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) { throw "Compilacion fallida (exit $LASTEXITCODE)" }
Write-Host "OK -> compila sin errores"
```

## T1 - Build debug + instalar en device

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
& "$PSScriptRoot\gradlew.bat" :app:assembleDebug --no-daemon --console=plain
# APK -> app\build\outputs\apk\debug\app-debug.apk

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$dev = (& $adb devices | Select-String "device$" | ForEach-Object { ($_ -split "\s+")[0] } | Select-Object -First 1)
& $adb -s $dev install -r ".\app\build\outputs\apk\debug\app-debug.apk"
```

## T2 - Build release + versionar APK

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
& "$PSScriptRoot\gradlew.bat" assembleRelease --no-daemon --console=plain
# APK -> app\build\outputs\apk\release\app-release.apk
```

Script automatizado (`build-release.ps1`) que ademas:
1. Lee `versionCode` y `versionName` de `app/build.gradle.kts`
2. Compila release
3. Copia el APK a `releases/<nombre>-<version>.apk`
4. Auto-incrementa `versionCode` y `versionName` (1.0.N -> 1.0.N+1)
5. Opcionalmente hace commit con `-Message "..."`

```powershell
.\build-release.ps1 -Message "feat(player): nuevo control"
```

## T3 - Instalar en device + copiar a Download

Instala el APK en el telefono y ademas deja una copia en `/sdcard/Download/` con el
numero de version en el nombre, para tenerlo descargable desde el gestor de archivos.

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$dev = (& $adb devices | Select-String "device$" | ForEach-Object { ($_ -split "\s+")[0] } | Select-Object -First 1)

# Leer version de app/build.gradle.kts
$gc = Get-Content ".\app\build.gradle.kts" -Raw
$versionName = [regex]::Match($gc, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value

# APK a instalar (usar el de releases/ con nombre versionado, o el de build/)
$apk = ".\releases\<nombre>-$versionName.apk"
if (-not (Test-Path $apk)) { $apk = ".\app\build\outputs\apk\release\app-release.apk" }
$apkName = Split-Path $apk -Leaf

# Instalar (reemplaza la version anterior sin desinstalar)
& $adb -s $dev install -r $apk

# Copiar a Download del telefono con nombre versionado
& $adb -s $dev push $apk "/sdcard/Download/$apkName"
```

Script automatizado (`install-release.ps1`) que ademas:
1. Busca el APK mas reciente en `releases/` (ya con nombre versionado por `build-release.ps1`)
2. Detecta devices por modelo (Samsung, Xiaomi, etc.)
3. Instala con `adb install -r` en cada device encontrado
4. Copia el APK a `/sdcard/Download/<nombre>-<version>.apk` en cada device

```powershell
.\install-release.ps1 -Device all      # todos los devices
.\install-release.ps1 -Device samsung  # solo Samsung
```

## Notas

- **Firma del release**: usa la clave de debug (`signingConfig = signingConfigs.getByName("debug")`)
  para mantener firma estable y permitir updates sin desinstalar.
- **R8/ProGuard**: activado en release (`isMinifyEnabled = true`, `isShrinkResources = true`).
- **`local.properties`** no se versiona (contiene `sdk.dir`).
- **`--no-daemon`**: evita procesos background de Gradle tras terminar.
- Para validacion completa en device (captura + verificacion funcional), ver workflow `validate`.
