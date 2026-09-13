# Plan — TD-084: los vídeos viajan con la asignación

## El problema

Mauro confecciona un training de 30 ejercicios para NIKO, se lo asigna, y en el teléfono de
ella aparece el training **sin vídeos**. Asignar envía el training, no los medios.

Hoy un vídeo llega a otro dispositivo por una sola vía:

```
manifiesto (videos.json en GitHub)  →  repo/<id>.<rev>.mp4   cualquier teléfono lo baja solo
selector del app                    →  own/<id>.mp4          privado del dispositivo, no viaja
```

COLUMNA se ve en su teléfono porque sus siete ejercicios **están publicados en el
manifiesto**. *On Your Marks* no, porque sus vídeos entraron uno a uno por el selector.

Asignarlos a mano no es opción: son 30, con el error humano que eso implica.

## Lo que ya está hecho

La maquinaria pesada existe y está probada en producción con COLUMNA:

| Pieza | Estado |
|---|---|
| `VideoRepository` — cola, prioridad urgente, revisiones, caché, reintentos | funciona |
| `VideoCache` — `own/` gana sobre `repo/`, poda de revisiones viejas | funciona |
| Descarga solo por wifi salvo que se active lo contrario | funciona |
| `net/Http.request(method, url, headers, …)` | ya acepta cabeceras |
| `Supabase.headers(accessToken)` | ya construye la autenticación |
| `AuthStore.accessToken()` | token con refresco automático |

Y el punto de pivote estaba anotado desde el principio, en `VideoRepository`:

```kotlin
// Un solo sitio a proposito: TD-063 lo cambia por uno por usuario
// (/users/<uid>/videos.json) tocando esta constante y nada mas.
const val MANIFEST_URL = "https://raw.githubusercontent.com/..."
```

## La restricción que lo condiciona todo

**El dispositivo que recibe no tiene cuenta.** Solo el entrenador inicia sesión; los demás
eligen su nombre de una lista y leen con la clave publicable. Está escrito en
`AssignmentRepository`: *"Para el dispositivo que recibe la sincronización es solo de
bajada"*.

Eso descarta la solución obvia —bucket privado y que cada teléfono se autentique—, porque
el teléfono de NIKO no tiene con qué autenticarse.

**La salida son URLs firmadas.** El bucket se queda privado; el entrenador genera al
publicar una URL que lleva su propio permiso incrustado, y esa URL va en el manifiesto. El
teléfono que recibe no necesita credenciales: descarga y ya. Y `Downloader.download` no
hay que tocarlo, porque la firma viaja en la URL y no en una cabecera.

**Hasta dónde llega la privacidad, dicho claro:** quien tenga la URL puede descargar
mientras la firma siga viva. Es el mismo nivel que ya tienen los trainings asignados, que
cualquiera con la clave publicable puede leer. Esto no lo empeora, pero tampoco lo arregla;
si algún día hace falta privacidad de verdad, el cambio es que los dispositivos receptores
tengan identidad, y eso es otro TD.

## Fases

### Fase A — el manifiesto deja de ser un fichero en GitHub

Tabla `videos` en Supabase, leída con la clave publicable igual que `profiles` y
`assignments`: `exercise_id`, `file`, `rev`, `bytes`, `url`, `updated_at`.

`VideoRepository` la lee con `Http.request` + `Supabase.headers()` en vez de
`Downloader.fetchText(MANIFEST_URL)`, y arma el mismo `VideoManifest` que ya sabe manejar.
**Todo lo de abajo —caché, revisiones, cola— no se entera.**

Al acabar esta fase COLUMNA sigue funcionando igual, pero por el camino nuevo. Es la
prueba de que el pivote no rompió nada.

### Fase B — subir desde la PC

Script que recibe una carpeta de `<exerciseId>.mp4`, los sube a un bucket privado, genera
las URLs firmadas y escribe las filas.

Va en la PC y no en el app **porque la clave secreta vive ahí**: `service_role` no puede
estar dentro de un APK, y para escribir en Storage hace falta permiso de escritura. Enlaza
con **TD-066**, que ya está abierto para esta misma familia de herramientas.

### Fase C — caducidad

Las URLs firmadas expiran. Dos medidas:

- Expiración larga al generarlas, y se regeneran al republicar.
- Si una descarga responde 403, se relee el manifiesto una vez antes de rendirse: es la
  señal de que la firma caducó, y el manifiesto nuevo trae una válida.

### Fase D — *opcional, solo si se pide*

Publicar desde el teléfono, sin PC. Necesita subida binaria desde el app, que hoy no
existe: `Http.request` manda texto. **Queda fuera de alcance** salvo que se pida.

## Riesgos

- **Cuota de Storage.** 30 vídeos a ~5 MB son ~150 MB. El plan gratuito de Supabase da del
  orden de 1 GB, así que entra, pero conviene mirarlo antes de subir una biblioteca entera.
- **Datos de quien recibe.** Ya está cubierto: `downloadsAllowed()` solo descarga por wifi
  salvo que se active lo contrario. Conviene comprobarlo en su teléfono, no darlo por hecho.
- **Los ids `custom_…`.** Seis de los diez de *On Your Marks* usan ids nacidos de un
  contador local (`custom_1770000000000`). Funcionan porque la asignación los transporta,
  pero para contenido publicado deberían ser ids de catálogo. **A decidir antes de la fase
  B**, porque una vez subidos con esa clave, cambiarlos obliga a resubir.

## Cómo se prueba

1. COLUMNA sigue viéndose en los dos teléfonos tras la fase A.
2. Se publica *On Your Marks* y el teléfono de NIKO baja los vídeos **sin que ella toque
   nada**, solo con la asignación.
3. Se corta el wifi: no descarga, y no rompe nada.
4. Se sube una revisión nueva de un vídeo y el teléfono la sustituye.
5. Un ejercicio sin vídeo publicado sigue comportándose como hoy.
