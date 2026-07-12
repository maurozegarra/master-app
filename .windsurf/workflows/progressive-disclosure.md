---
description: Principio de UI - mostrar controles solo cuando aplican (divulgacion progresiva vs atenuar)
---

# Divulgacion progresiva

**Los controles aparecen solo cuando son necesarios.** Las sub-opciones se muestran
en linea (dentro de su fila/tarjeta) SOLO cuando aplican; el resto del tiempo NO se
dibujan en absoluto.

En Compose: emitir el composable dentro de un `if (condicion) { ... }` (equivalente
a `View.VISIBLE` / `View.GONE`), nunca dejarlo siempre presente.

## Divulgacion anidada

La divulgacion es anidable: un sub-control puede revelar otro.

Ejemplo en Athletic: en `ExerciseEditorScreen.kt`, el selector de beep sound aparece
solo si `cfg.finalCount > 0`. Al abrir el dialogo del picker, revela mas opciones
(preview, select) — todas condicionadas al mismo estado.

## Atenuar vs divulgacion — elegir UNO

- **Divulgacion progresiva**: las sub-opciones dependientes de un estado
  **desaparecen** por completo cuando el estado esta apagado. Usar para
  sub-opciones que dependen de un toggle o valor separado.
- **Atenuar**: un ajuste ligado a su propio switch **en la misma fila** queda
  VISIBLE pero ATENUADO (p. ej. `alpha 0.4` + deshabilitado) cuando el switch
  esta OFF. Usar cuando el control es parte logica de la misma fila que su toggle.

**Regla**: al agregar un control con sub-opciones, elegir UN patron y mantenerlo.
Sub-opciones dependientes de un estado -> divulgacion progresiva (mostrar/ocultar).
Ajuste ligado a un switch de su misma fila -> atenuar.

## Anti-patrones

- Renderizar siempre un control y ocultarlo visualmente con `alpha 0` o
  `Modifier.size(0.dp)` — usar `if (condicion)` para que no se componga.
- Mezclar ambos patrones en el mismo grupo logico (algunas sub-opciones atenuadas,
  otras ocultas) — confunde al usuario.
