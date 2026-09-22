package com.maurozegarra.master.model

import java.text.Normalizer

/**
 * Cómo se dibuja el lado de un paso en el player (TD-153).
 *
 * **Una marca de ancho fijo, no la palabra.** El lado va colgado a la izquierda del número
 * grande, y el 22-sep "Derecha · 1/3" junto a un reloj de 00:20 se salía por el borde:
 * NIKO veía "HA" y "DA" y no sabía cuál tocaba. En inglés -Left, Right- cabía de
 * casualidad. Una flecha mide lo mismo en cualquier idioma y se lee sin leer.
 *
 * La palabra entera no se pierde: va en la línea del nombre del ejercicio, que tiene todo
 * el ancho y se encoge para caber.
 *
 * **La flecha no se refleja.** Mirando la pantalla, la derecha del atleta es la derecha de
 * la pantalla, así que → es derecha tal cual.
 */
object SideMark {

    /**
     * La marca para [side], o null si el paso no tiene lado.
     *
     * Las direcciones conocidas son una flecha. Cualquier otra etiqueta -un lado que el
     * coach llame de otra forma- se dibuja como puntos de posición: el lado 2 de 4 es
     * "○●○○". No dice cuál es, pero dice cuántos quedan, y la palabra está en el título.
     */
    fun of(side: String, index: Int, count: Int): String? {
        if (side.isBlank()) return null
        arrow(side)?.let { return it }
        if (count > 1) return (0 until count).joinToString("") { if (it == index) "●" else "○" }
        return side.trim().take(1).uppercase()
    }

    private fun arrow(side: String): String? = when (normalize(side)) {
        "izquierda", "izq", "left", "l" -> "←"
        "derecha", "der", "right", "r" -> "→"
        "adelante", "frente", "delante", "front", "forward" -> "↑"
        "atras", "detras", "back", "backward" -> "↓"
        else -> null
    }

    /** Sin tildes ni mayúsculas: "Atrás" y "atras" son el mismo lado. */
    private fun normalize(s: String): String =
        Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
