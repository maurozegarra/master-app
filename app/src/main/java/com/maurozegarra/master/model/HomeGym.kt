package com.maurozegarra.master.model

/**
 * El equipo de la casa, el mismo para los dos atletas. La fuente es `docs/equipo.md`.
 *
 * Esta en el codigo para que un test pueda comprobar que cada peso de una rutina se puede
 * armar de verdad (TD-130): el 18-sep se entrego una rutina con un hip thrust de 70 kg
 * sobre una barra de 6, que con estos discos no existe.
 */
object HomeGym {
    /** Las barras, en kg. Todas de disco olimpico. */
    val BARS = listOf(6.0, 10.0, 15.0, 20.0, 22.5)

    /**
     * Mancuernas fijas, en kg. La de 22.5 no estaba en el inventario del 18-sep; la agrego el
     * usuario el 19, y es la que evita el salto de 20 a 25.
     */
    val DUMBBELLS = listOf(1.0, 2.0, 2.5, 3.0, 4.0, 5.0, 7.5, 10.0, 12.5, 15.0, 17.5, 20.0, 22.5, 25.0)
}
