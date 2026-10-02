package com.maurozegarra.master.model

/** Un tramo de series seguidas que dicen lo mismo: [first] a [last], con [set] de muestra. */
data class SetRun(val first: Int, val last: Int, val set: SetRecord)

/**
 * Las series de un ejercicio del historial, como las lee una persona (TD-181).
 *
 * El 1-oct el usuario lo dijo con una captura: el curl-up eran doce filas de "10 s" que no
 * decian nada, y la previa del mismo training decia mas -"12 × 10 s · rest 3 s–30 s"-.
 */
object SetRuns {

    /**
     * Las series seguidas que dicen lo mismo -valor, peso, respuesta, si se salto- van en un
     * tramo: "1–12  10 s". Una distinta lo corta y se sigue viendo sola. El descanso no
     * cuenta para cortar: va resumido en la cabecera ([rests]), y en una piramide partiria
     * los aguantes en tramos que solo se distinguen por eso.
     */
    fun of(sets: List<SetRecord>): List<SetRun> {
        val tramos = mutableListOf<SetRun>()
        sets.forEachIndexed { i, sr ->
            val ultimo = tramos.lastOrNull()
            if (ultimo != null && ultimo.set.copy(restSec = null) == sr.copy(restSec = null)) {
                tramos[tramos.lastIndex] = ultimo.copy(last = i)
            } else {
                tramos += SetRun(i, i, sr)
            }
        }
        return tramos
    }

    /**
     * Los descansos distintos ENTRE series, como los cuenta la previa: el de despues de la
     * ultima no, porque ahi o no hay o es el respiro para contestar (TD-156). Vacio en una
     * sesion de antes de que se guardaran.
     */
    fun rests(sets: List<SetRecord>): List<Int> =
        sets.dropLast(1).mapNotNull { it.restSec }.distinct().sorted()
}
