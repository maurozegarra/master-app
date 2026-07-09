---
description: Descargar y empaquetar una fuente (TTF) en res/font y usarla en la app Android (p. ej. fuente monoespaciada con cero diferenciado 0 != O)
---

Procedimiento verificado en este equipo corporativo (Netskope + JFrog, sin permisos de admin)
para incrustar una fuente en la app en vez de depender de la fuente del sistema. Caso típico:
necesitar un **monoespaciado con cero punteado/tachado** para diferenciar `0` de `O`.

Fuentes recomendadas (licencia permisiva, cero diferenciado):
- **Roboto Mono** (Apache-2.0) — cero punteado.
- **JetBrains Mono** (OFL) — cero punteado/tachado.
- **DejaVu Sans Mono** — cero tachado.

## 1. Descargar el TTF a res/font
`curl.exe` funciona aunque `Invoke-WebRequest` falle o quede bloqueado: usa el almacén de
certificados de Windows, que ya confía en el CA corporativo de Netskope. SIEMPRE usar `-L`
(GitHub redirige a la CDN). El nombre del archivo en `res/font` debe ser **minúsculas,
sin guiones ni versiones** (solo `[a-z0-9_]`).

// turbo
```powershell
$out = ".\app\src\main\res\font"; New-Item -ItemType Directory -Force -Path $out | Out-Null
curl.exe -L --ssl-no-revoke -o "$out\jetbrains_mono.ttf" "https://github.com/JetBrains/JetBrainsMono/raw/master/fonts/ttf/JetBrainsMono-Medium.ttf"
if (Test-Path "$out\jetbrains_mono.ttf") { "size=" + (Get-Item "$out\jetbrains_mono.ttf").Length } else { "FALLO" }
```
Verificar que el tamaño sea razonable (JetBrains Mono ~270 KB, Roboto Mono ~290-300 KB). Si sale
unos pocos KB, probablemente bajó una página de error HTML: revisar la URL.

URLs verificadas:
- Roboto Mono: `https://github.com/google/fonts/raw/main/apache/robotomono/static/RobotoMono-Medium.ttf`
- JetBrains Mono: `https://github.com/JetBrains/JetBrainsMono/raw/master/fonts/ttf/JetBrainsMono-Medium.ttf`

Notas:
- `Invoke-WebRequest` puede devolver "Exit code could not be determined / No output" o quedar
  pendiente de aprobación; `curl.exe -L` fue lo que funcionó de forma fiable.
- **`--ssl-no-revoke` puede ser necesario**: algunos hosts/CDN (p. ej. el raw de `github.com/JetBrains`)
  al ser interceptados por Netskope fallan con `schannel: CRYPT_E_NO_REVOCATION_CHECK (0x80092012)`
  porque schannel no puede comprobar la revocación del certificado. `--ssl-no-revoke` la omite.
  (La descarga de `github.com/google` funcionó sin esa bandera.)
- La descarga es una petición externa: NO auto-ejecutar sin criterio; confirmar la URL/licencia.

## 2. Referenciar la fuente en código
AGP genera `R.font.<nombre_archivo_sin_extension>`. Cargarla con `ResourcesCompat.getFont`
y cachearla (evita recargas). Hacer fallback a `Typeface.MONOSPACE` si algo falla:
```kotlin
import androidx.core.content.res.ResourcesCompat

private var monoCache: Typeface? = null
fun monoTypeface(context: Context): Typeface =
    monoCache ?: (runCatching { ResourcesCompat.getFont(context, R.font.roboto_mono) }
        .getOrNull() ?: Typeface.MONOSPACE).also { monoCache = it }
```
Aplicar al TextView: `textView.typeface = monoTypeface(context)`.
Opcional para números: `textView.fontFeatureSettings = "tnum"` (dígitos tabulares).

## 3. Compilar y verificar
// turbo
```powershell
$env:JAVA_HOME = 'C:\Users\mzegarra_ide\Downloads\android-studio\jbr'
& "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.4.1-bin\*\gradle-9.4.1\bin\gradle.bat" assembleDebug --no-daemon --console=plain 2>&1 | Select-Object -Last 5
```
Instalar por ADB (ver workflows `connect-phone` y `validate`) y confirmar en pantalla que `0` se
distingue de `O` (memo con texto tipo `O0Oo`).

## Gotchas
- Nombre de archivo inválido en `res/font` (mayúsculas/guiones/números al inicio) => error de recursos.
- Si se usa `res/font` como XML de familia (downloadable fonts de Google) hace falta el array de
  certificados y Play Services; **embeber el TTF es más simple y funciona offline**.
- La fuente del sistema (`Typeface.MONOSPACE`) NO garantiza cero diferenciado en todos los equipos
  (p. ej. Samsung/One UI): por eso se embebe.
