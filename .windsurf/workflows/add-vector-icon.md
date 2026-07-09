---
description: Crear un icono vectorial (VectorDrawable) estilo One UI reutilizando paths canonicos de Material Design y convirtiendolos a contorno fino tintable
---

# Agregar un icono vectorial estilo One UI

Metodo para crear un VectorDrawable (p. ej. `res/drawable/ic_settings.xml`, un engranaje de header).
La clave NO es dibujar el icono desde cero con arcos/geometria (eso suele fallar), sino:

1. **Reutilizar el `pathData` canonico de Material Design / Material Symbols** como base geometrica.
2. **Convertir el relleno a contorno fino** para el look de linea de One UI.
3. Mantenerlo **tintable** (un solo color, sin degradados) para que herede el color del tema.

## Principios (por que funciona)

- Los iconos de Material vienen en un **viewport 24x24** con `pathData` ya optimizado y probado.
  Copiar ese path exacto evita errores de curvas Bezier y proporciones.
- El estilo One UI es de **linea delgada**: por eso usamos `fillColor` transparente + `strokeColor` +
  `strokeWidth` ~1.3-1.6, en vez del path relleno tipico de Material.
- Un solo `strokeColor` (o `@android:color/white`) hace el drawable **tintable** con `app:tint`.

## Pasos

### 1. Conseguir el `pathData` base

Opciones, en orden de preferencia:

- **COPIA EXACTA desde el APK del sistema** (mejor para replicar iconos propietarios de One UI /
  Samsung). Ver seccion "Extraer el path exacto de un APK" mas abajo. Da el `pathData` identico.
- **Recordar/derivar el path canonico** del icono de Material Design (24x24). Ej: settings, close,
  arrow, add, delete, palette, etc. Son paths estables y conocidos.
- Si no lo recuerdas con certeza, **buscar el SVG oficial** de Material Symbols
  (https://fonts.google.com/icons) y copiar el atributo `d` del `<path>`.
- Verificar que el path este pensado para viewport **24x24** (si el SVG es 0 0 960 960 de Material
  Symbols nuevo, o reescalar el `viewportWidth/Height` a ese tamano, o usar la variante 24dp).

#### Extraer el path exacto de un APK (copia fiel de One UI)

Cuando el usuario quiere una **copia fiel** de un icono del sistema (p. ej. los iconos del panel de
Volumen: Ringtone/Media/Notifications), extrae el VectorDrawable real con `aapt2 dump xmltree`.
No hace falta root: los APK de sistema se pueden `pull` por adb.

// turbo
1. Ubicar `aapt2` y el APK que contiene el icono (Settings de Samsung = `SecSettings`):

```powershell
$sdk="$env:LOCALAPPDATA\Android\Sdk"; $adb="$sdk\platform-tools\adb.exe"
$aapt2=(Get-ChildItem "$sdk\build-tools" -Recurse -Filter aapt2.exe | Sort-Object FullName -Descending | Select-Object -First 1).FullName
$dev=(& $adb devices | Select-String "device$" | ForEach-Object { ($_ -split "\s+")[0] } | Select-Object -First 1)
& $adb -s $dev shell pm path com.android.settings
```

// turbo
2. Traer el APK (ajusta la ruta que devolvio `pm path`):

```powershell
& $adb -s $dev pull /system/priv-app/SecSettings/SecSettings.apk "$env:TEMP\SecSettings.apk"
```

3. Buscar el nombre del drawable (por palabras clave):

```powershell
& $aapt2 dump resources "$env:TEMP\SecSettings.apk" > "$env:TEMP\res.txt" 2>$null
Select-String -Path "$env:TEMP\res.txt" -Pattern "drawable/[^ ]*(audio|media|ring|notif|sound|volume|music|note)" |
  ForEach-Object { ($_.Line -replace '^\s+','') } | Sort-Object -Unique
```

4. Volcar el XML del icono a un archivo (viene en UTF-16 con null bytes; NO abrir directo, extraer con regex):

```powershell
& $aapt2 dump xmltree "$env:TEMP\SecSettings.apk" --file res/drawable/tw_ic_audio_media_note.xml > "$env:TEMP\ico.txt" 2>$null
$t = Get-Content "$env:TEMP\ico.txt" -Raw
[regex]::Matches($t,'viewport(Width|Height)\(0x[0-9a-f]+\)=([0-9.]+)') | ForEach-Object { $_.Value }
[regex]::Matches($t,'pathData\(0x[0-9a-f]+\)="([^"]*)"') | ForEach-Object { 'PATH: ' + $_.Groups[1].Value }
[regex]::Matches($t,'fillColor\(0x[0-9a-f]+\)=(#[0-9a-fA-F]+)') | ForEach-Object { 'FILL: ' + $_.Groups[1].Value }
```

5. Reconstruir el drawable con el `viewportWidth/Height` EXACTO y cada `pathData` extraido.
   - Cambia `fillColor` a `@android:color/white` (o deja que lo tinte `android:tint`).
   - **Respeta el viewport original** (p. ej. Samsung usa `42x84`). Si difiere de 24x24, ajusta
     `android:width/height` manteniendo la proporcion del viewport (42:84 = 1:2) para no distorsionar.
   - Muchos iconos One UI usan **relleno con contorno interno** (subpath opuesto que crea un hueco):
     eso ya da el aspecto "outline" con `fillColor`, sin necesitar `strokeColor`.
   - Ejemplo: iconos de audio (`ic_ringtone` altavoz, `ic_media` corchea) copiados exactos de
     `SecSettings.apk` conservan su viewport original.

Referencia de nombres utiles en SecSettings: `tw_ic_audio_sound_ringtone` (altavoz),
`tw_ic_audio_media_note` (corchea), `tw_ic_audio_noti_mtrl` (campana), `tw_ic_audio_system_mtrl`.

### 2. Crear el archivo drawable

Crear `app/src/main/res/drawable/ic_<nombre>.xml`. Plantilla **contorno (One UI)**:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="@android:color/transparent"
        android:strokeColor="@android:color/white"
        android:strokeWidth="1.4"
        android:strokeLineJoin="round"
        android:strokeLineCap="round"
        android:pathData="AQUI_VA_EL_PATH_CANONICO" />
</vector>
```

Notas:
- Ajusta `strokeWidth` entre **1.3 y 1.6** hasta que el grosor iguale a los iconos vecinos.
- Usa `strokeLineJoin="round"` y `strokeLineCap="round"` para esquinas suaves estilo One UI.
- Si el icono debe ir **relleno** (solido), usa `android:fillColor="@android:color/white"` y quita los
  atributos de stroke.
- Para multiples subformas, agrega varios `<path>` (o usa `<group>` para transformaciones).

### 3. Usarlo en un layout / boton

Ejemplo real, tal como se usa el engranaje en el header (`activity_main.xml`):

```xml
<ImageButton
    android:id="@+id/btnGlobalSettings"
    android:layout_width="44dp"
    android:layout_height="44dp"
    android:padding="10dp"
    android:background="?attr/selectableItemBackgroundBorderless"
    android:src="@drawable/ic_settings"
    android:scaleType="fitCenter"
    android:contentDescription="@string/global_settings"
    android:tint="?attr/colorOnSurface" />
```

- Usa **`android:tint="?attr/colorOnSurface"`** para que siga el tema (no hardcodees color).
- Boton de **44dp con `padding=10dp`** -> icono ~24dp con area tactil comoda.
- `scaleType="fitCenter"` para centrar el vector.

### 4. Verificar en dispositivo

// turbo
1. Compilar:

```powershell
$env:JAVA_HOME='C:\Users\mzegarra_ide\Downloads\android-studio\jbr'
$gradle=(Resolve-Path "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.4.1-bin\*\gradle-9.4.1\bin\gradle.bat").Path
& $gradle :app:assembleDebug --no-daemon --console=plain
```

// turbo
2. Instalar y tomar captura para revisar el trazo (ver `validate.md` para el flujo completo de adb):

```powershell
$sdk="$env:LOCALAPPDATA\Android\Sdk"; $adb="$sdk\platform-tools\adb.exe"
$dev="<serial-o-ip:puerto>"   # serial de sesion (ver workflow connect-phone)
& $adb -s $dev install -r ".\app\build\outputs\apk\debug\app-debug.apk"
& $adb -s $dev shell screencap -p /sdcard/_icon.png
& $adb -s $dev pull /sdcard/_icon.png ".\_icon.png"
```

3. Comparar grosor y proporcion con los iconos vecinos. Ajustar `strokeWidth` / `viewport` si hace falta.

## Ejemplos de referencia

- Icono de **relleno** tintable (viewport 24x24): copia un `pathData` canonico de Material (p. ej.
  el ojo `visibility`) con `fillColor="@android:color/white"`; sirve para notificaciones y launcher.
- Icono de **contorno** One UI: mismo path con `fillColor` transparente + `strokeColor` +
  `strokeWidth` ~1.4 tintable.
- Copia fiel desde el sistema: las **llaves `{ }`** de "Developer options" salen de `SecSettings.apk`
  (drawable `ic_developer_options`, **viewport 20x20**, relleno tintable). Buscar con la keyword
  `develop|dev_|code|brace|data_object` en el volcado de recursos.

## Checklist rapido

- [ ] `pathData` canonico (no inventado a mano) en viewport 24x24.
- [ ] Contorno fino (`strokeWidth` 1.3-1.6, `fillColor` transparente) para look One UI.
- [ ] Un solo color / tintable con `app:tint`.
- [ ] `strokeLineJoin`/`strokeLineCap` = `round`.
- [ ] Verificado en dispositivo y consistente con iconos vecinos.
