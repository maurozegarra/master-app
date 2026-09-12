package com.maurozegarra.master.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Radios y medidas compartidas.
 *
 * [card] y [row] no son dos formas de decir lo mismo: marcan **jerarquía**. Un contenedor
 * que ocupa la pantalla —un bloque de Ajustes, la caja de perfiles— redondea más que una
 * fila dentro de una lista, y así el ojo distingue "esto agrupa" de "esto es un elemento"
 * sin leer nada.
 *
 * Antes solo existía [card] y solo lo usaban cuatro pantallas; el resto llevaba el número
 * a mano, y salieron cuatro radios distintos —14, 16, 18 y 20— sin que nadie lo decidiera.
 * Al ponerles nombre se vio que no era desorden del todo: los contenedores tiraban a 20 y
 * las filas a 16, con los selectores descolgados en 14 y la TrainingCard en 18.
 */
object Dims {
    /** Contenedor que agrupa: bloques de Ajustes, caja de perfiles, fichas de sección. */
    val card = 20.dp

    /** Fila de una lista: sesiones, workouts, variantes, ejercicios, trainings. */
    val row = 16.dp

    /**
     * Espacio entre el borde de una fila y su contenido.
     *
     * 16dp porque es lo que Material 3 especifica como inset de un *list item*, no porque
     * fuera lo más repetido: estaba a 12 en cinco filas, a 16 en cuatro y a 14 en una, y
     * contar cuál ganaba solo habría consagrado el que se copió más veces.
     *
     * Además encaja en la rejilla de 8dp de Material, donde 4 es la subdivisión para
     * elementos pequeños. El 14 que había en la tarjeta del preview no estaba en ninguna
     * de las dos: no es múltiplo de 4.
     */
    val rowPadding = 16.dp

    val button = 28.dp
    val buttonSmall = 12.dp
    val field = 12.dp
    val buttonHeight = 52.dp
}
