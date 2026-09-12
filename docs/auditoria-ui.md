# Auditoría de coherencia UI — TD-083

Escaneo de todo el proyecto buscando incoherencias, para no ir cazándolas de una en una.
Ordenado por lo que más se nota, no por lo que más cuesta.

---

## 1. Acciones en listas: swipe contra menú de 3 puntos

**Ya estaba decidido, y quedaron dos pantallas sin migrar.** TD-039 puso swipe en las
TrainingCards *"en reemplazo del menú de 3 puntos"*, y TD-057 lo extendió a WorkoutRow y
VariantRow. Nadie volvió a por las otras dos.

| Lista | Pantalla | Mecanismo | Reordena |
|---|---|---|---|
| Trainings | `MasterScreen` | **swipe** | sí |
| Workouts | `TrainingEditorScreen` | **swipe** | sí |
| Variantes | `VariantListScreen` | **swipe** | sí |
| Ejercicios | `WorkoutEditorScreen` | 3 puntos | sí |
| Sesiones | `HistoryScreen` | 3 puntos | no |

`WorkoutEditorScreen` es la más llamativa: **arrastra para reordenar igual que las otras
tres**, pero al llegar a las acciones cambia de idioma.

En `HistoryScreen` el menú tiene una sola entrada, *Delete*. Un menú desplegable para una
acción es justo lo que el swipe resuelve mejor, y además libera los 48dp que hoy ocupa.

**Qué haría:** swipe en las dos. La dirección ya la fijaste tú en TD-039.

---

## 2. Chevrons: dos gramáticas distintas para lo mismo

- **`>` que gira 90°** al abrir — `PlayerScreen:259` (preview del training).
- **Dos iconos distintos**, `ExpandMore` / `ExpandLess` — `HistoryScreen:206` y `:337`,
  `ExerciseEditorScreen:207` y `:460`.

Cuatro sitios contra uno, pero el que está solo es el que tú prefieres. Cambiarlo es
sustituir el icono y añadir `.rotate(if (open) 90f else 0f)`.

---

## 3. No hay componente de badge: hay cinco escritos a mano

`HistoryScreen:183`, `:323`, `:398` y `MasterScreen:520`, `:532`. Todos repiten la misma
receta —`Box` + `clip(RoundedCornerShape(4.dp))` + fondo al 15% + texto 9sp Bold— pero cada
uno con sus colores. No hay un `Badge()` compartido, así que el siguiente que se añada
volverá a copiarse a mano y podrá salir distinto sin que nadie lo note.

---

## 4. Colores fuera del tema

Estos no pasan por `AppTheme`, así que no siguen el acento ni responderían a un cambio de
tema:

| Color | Qué es | Dónde |
|---|---|---|
| `0xFF4CAF50` | verde "completo" | `HistoryScreen:181`, `:283` |
| `0xFFFFA000` | ámbar "saltado" | `HistoryScreen:282`, `:399`, `:404` |
| `0xFF2E9E5B` | verde del glifo | `ChooseExerciseScreen:112`, `PlayerScreen:291` |
| `0xFFCFD3D6` | gris del switch | `CommonComponents:159` |

Los de `model/` (colores de etapa y acentos) **sí tienen su sitio**: son datos, no estilo.

---

## 5. Radios de esquina: nueve valores distintos

`2, 4, 8, 10, 12, 14, 16, 18, 20`.

Lo que más canta es que **la tarjeta de una lista no tiene un radio**, tiene dos:

- **14dp** — `MasterScreen`, `ChooseExerciseScreen`, `ChooseWorkoutScreen`
- **16dp** — `HistoryScreen`, `TrainingEditorScreen`, `VariantListScreen`,
  `WorkoutEditorScreen`, `ExerciseHistoryScreen`

Son dos pantallas de lista pegadas en la navegación con tarjetas de esquina distinta.

---

## 6. Tipografía del título de fila

| Tamaño | Pantallas |
|---|---|
| **18sp** | `MasterScreen` |
| **16sp** | `HistoryScreen`, `TrainingEditorScreen`, `WorkoutEditorScreen`, `VariantListScreen`, `ChooseExerciseScreen` |
| **15sp** | `ExerciseHistoryScreen` |

El 18 de `MasterScreen` se defiende —es la pantalla raíz—, pero el 15 de
`ExerciseHistoryScreen` no parece decidido, parece heredado.

---

## 7. Padding de tarjeta

**16dp** en casi todas; **14dp** en las dos tarjetas de `PlayerScreen` (`:215`, `:1176`).

---

## Orden que propongo

1. **Swipe en `HistoryScreen` y `WorkoutEditorScreen`.** Es lo que preguntaste y lo que
   más se nota. Cierra una decisión que ya estaba tomada.
2. **Chevron `>` en todas partes.** Cuatro sitios, cambio mecánico.
3. **Un `Badge()` compartido**, y de paso los colores de estado al tema. Las dos cosas
   tocan el mismo código, así que salen juntas.
4. **Radio de tarjeta único**, 14 o 16, y padding 16 en todas.
5. **Título de fila**, decidir si `ExerciseHistoryScreen` sube a 16.

Los puntos 4 y 5 son barridos; conviene hacerlos de una pasada y verlos juntos, no de
uno en uno como el resto.
