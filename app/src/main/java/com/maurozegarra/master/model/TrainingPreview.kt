package com.maurozegarra.master.model

/**
 * Lo que la pantalla previa dice de un ejercicio (TD-173): la receta entera, no un paso.
 *
 * Antes cada fila mostraba el PRIMER paso de trabajo, y el curl-up de McGill se leia "0:10":
 * un aguante de doce, que parecia la duracion del ejercicio. Tampoco salian los pesos. El
 * usuario lo dijo el 26-sep: "casi no muestra detalle".
 *
 * [work] y [weights] van por serie, del primer lado: los dos lados de una serie llevan lo
 * mismo. [rests] son los descansos distintos ENTRE series; el de despues de la ultima no
 * cuenta, porque ahi o no hay o es el respiro para contestar (TD-156), que no es un descanso.
 */
data class PreviewItem(
    val name: String,
    val exerciseId: String,
    val showVideo: Boolean,
    val kind: Kind,
    val work: List<Int>,
    val weights: List<Double>,
    val sides: Int,
    val alternate: Boolean,
    val rests: List<Int>,
) {
    enum class Kind { TIME, REPS, DISTANCE }
}

/** Un workout en la pantalla previa. [rounds] solo en un circuito (TD-137). */
data class PreviewBlock(
    val index: Int,
    val title: String,
    val rotating: Boolean,
    val variant: String,
    val circuit: Boolean,
    val rounds: Int,
    val estimatedSec: Int,
    val items: List<PreviewItem>,
)

/** Las palabras de la receta. Vienen de Strings; el modelo no depende de i18n. */
data class PreviewWords(
    val rest: String = "rest",
    val eachSide: String = "each side",
    val alternating: String = "sides alternating",
    val reps: String = "reps",
    val kg: String = "kg",
)

object TrainingPreview {

    /**
     * Los bloques de la cola que va a correr el player, con cada ejercicio resumido.
     *
     * Se arma sobre la cola y no sobre el training: es lo que de verdad va a pasar, con la
     * variante activa de un rotativo, el orden de un circuito y los pesos ya resueltos.
     * El tiempo de cada bloque usa [PlayerStep.estimatedSec], el mismo estimado que la barra
     * del player: antes se sumaban solo los pasos con reloj y un bloque de repeticiones
     * parecia durar lo que sus descansos.
     */
    fun of(steps: List<PlayerStep>): List<PreviewBlock> =
        steps.groupBy { it.workoutIndex }.entries.sortedBy { it.key }.map { (wi, list) ->
            val first = list.first()
            val works = list.filter { it.kind == StepKind.WORK }
            // Por instancia (exerciseIndex) y no por nombre: el mismo ejercicio dos veces en
            // un workout son dos filas (TD-100). groupBy conserva el orden de aparicion.
            val items = works.groupBy { it.exerciseIndex }.map { (ei, ws) ->
                val s0 = ws.first()
                val lado = ws.filter { it.sideIndex == s0.sideIndex }
                PreviewItem(
                    name = s0.ownerName,
                    exerciseId = s0.ownerExerciseId,
                    showVideo = s0.showVideo,
                    kind = when {
                        s0.timeBased -> PreviewItem.Kind.TIME
                        s0.distance -> PreviewItem.Kind.DISTANCE
                        else -> PreviewItem.Kind.REPS
                    },
                    work = lado.map { if (it.timeBased) it.durationSec else it.reps },
                    weights = if (s0.weighted) lado.map { it.weightTotal } else emptyList(),
                    sides = s0.sideCount.coerceAtLeast(1),
                    // Alternando, al primer lado le sigue el otro en la MISMA serie (TD-156).
                    alternate = s0.sideCount > 1 && ws.size > 1 && ws[1].sideIndex != s0.sideIndex,
                    rests = list.filter {
                        it.kind == StepKind.REST && it.exerciseIndex == ei && it.setIndex < it.totalSets - 1 && it.durationSec > 0
                    }.map { it.durationSec }.distinct().sorted(),
                )
            }
            PreviewBlock(
                index = wi,
                title = first.workoutBaseName.ifBlank { first.workoutName },
                rotating = first.rotating,
                variant = first.variantName,
                circuit = first.circuit,
                rounds = if (first.circuit) (works.maxOfOrNull { it.slot } ?: 0) + 1 else 0,
                estimatedSec = list.sumOf { it.estimatedSec },
                items = items,
            )
        }

    /** "3 × 10 · 21/31/36 kg · rest 90 s". */
    fun describe(item: PreviewItem, w: PreviewWords = PreviewWords()): String = listOfNotNull(
        work(item, w),
        if (item.sides > 1) (if (item.alternate) w.alternating else w.eachSide) else null,
        weights(item, w),
        rest(item, w),
    ).joinToString(" · ")

    /** 45 -> "45 s", 180 -> "3 min", 90 -> "1:30". */
    fun duration(sec: Int): String = when {
        sec < 60 -> "$sec s"
        sec % 60 == 0 -> "${sec / 60} min"
        else -> "%d:%02d".format(sec / 60, sec % 60)
    }

    private fun work(item: PreviewItem, w: PreviewWords): String? {
        val v = item.work
        if (v.isEmpty()) return null
        fun unit(x: Int) = when (item.kind) {
            PreviewItem.Kind.TIME -> duration(x)
            PreviewItem.Kind.DISTANCE -> "$x m"
            PreviewItem.Kind.REPS -> x.toString()
        }
        // Tramos de series iguales: una piramide de aguantes se lee "6 × 10 s + 4 × 15 s".
        val tramos = mutableListOf<Pair<Int, Int>>()
        v.forEach { x -> if (tramos.lastOrNull()?.first == x) tramos[tramos.lastIndex] = x to tramos.last().second + 1 else tramos += x to 1 }
        return when {
            v.size == 1 && item.kind == PreviewItem.Kind.REPS -> "${v[0]} ${w.reps}"
            v.size == 1 -> unit(v[0])
            tramos.size == 1 -> "${v.size} × ${unit(v[0])}"
            // Todas distintas: una rampa, "12/10/8".
            tramos.all { it.second == 1 } -> when (item.kind) {
                PreviewItem.Kind.REPS -> "${v.joinToString("/")} ${w.reps}"
                else -> v.joinToString("/") { unit(it) }
            }
            else -> tramos.joinToString(" + ") { (x, n) -> if (n == 1) unit(x) else "$n × ${unit(x)}" }
        }
    }

    private fun weights(item: PreviewItem, w: PreviewWords): String? {
        val p = item.weights
        if (p.isEmpty() || p.all { it <= 0.0 }) return null
        return if (p.distinct().size == 1) "${kg(p[0])} ${w.kg}" else "${p.joinToString("/") { kg(it) }} ${w.kg}"
    }

    private fun rest(item: PreviewItem, w: PreviewWords): String? {
        val r = item.rests
        return when {
            r.isEmpty() -> null
            r.size == 1 -> "${w.rest} ${duration(r[0])}"
            else -> "${w.rest} ${duration(r.first())}–${duration(r.last())}"
        }
    }

    private fun kg(d: Double): String {
        val r = Math.round(d * 10)
        return if (r % 10 == 0L) (r / 10).toString() else (r / 10.0).toString()
    }
}
