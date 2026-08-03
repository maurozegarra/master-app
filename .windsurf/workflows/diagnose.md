---
description: Forzar diagnóstico de causa raíz antes de seguir parcheando cuando un fix no funcionó a la primera
---

# Workflow: Diagnóstico antes de parchear

Invocar con `/diagnose` cuando un cambio de código no produjo el resultado esperado.
El objetivo es **entender por qué** antes de proponer otro fix. No se toca código
hasta tener una hipótesis fundamentada en evidencia.

## Pasos

1. **Reproducir y medir el fallo real**
   - Confirmar con datos concretos (logs, dumpsys, frames, valores de variables)
     qué se esperaba y qué se obtuvo. No asumir, no intuir: medir.
   - Anotar la diferencia numérica o de comportamiento exacta.

2. **Identificar la API o mecanismo involucrado**
   - Nombrar explícitamente la API/clase/función del framework o librería
     que no se comporta como se esperaba (ej. `PlayerBus.state`,
     `WorkoutPlayerService.stopPlayer()`, `MutableStateFlow`).

3. **Investigar la causa raíz**
   - Leer la **documentación oficial** de la API involucrada.
   - Revisar el **código fuente** del framework (AOSP, librería) si la doc
     no aclara el comportamiento.
   - Buscar issues/discusiones de la comunidad (StackOverflow, GitHub) sobre
     el mismo síntoma.
   - El objetivo es entender el **mecanismo subyacente**, no encontrar un
     workaround que "a lo mejor funciona".

4. **Formular hipótesis de causa raíz**
   - Escribir una explicación causal: "X pasa porque Y, y la evidencia Z lo
     confirma". La hipótesis debe explicar **por qué** el fix anterior no
     funcionó y **qué** debe cambiar.

5. **Proponer fix basado en la hipótesis**
   - Solo ahora, con la hipótesis fundamentada, proponer el cambio de código.
   - Pedir confirmación al usuario antes de implementar (regla
     `No implementar sin autorización explícita del usuario`).

6. **Si el fix sigue sin funcionar**
   - NO acumular parches sobre parches. Volver al paso 1 con los nuevos datos.
   - Si después de 2 ciclos completos no se encuentra la causa raíz, escalar
   al usuario explicando qué se investigó y qué opciones quedan.

## Anti-patrón a evitar

> Parchear con fe: cambiar código sin entender por qué el cambio anterior
> falló, esperando que "a lo mejor ahora sí". Esto genera bucles de
> iteración que consumen tiempo y producen fixes frágiles.
