package com.maurozegarra.master.model

/**
 * Qué discos poner en cada lado de la barra (TD-130).
 *
 * Existe porque el player decía "NEXT: HIP THRUST · 55 KG" y la cuenta la hacía quien
 * entrenaba, entre series: restar la barra, dividir entre dos, armar el lado con los discos
 * que hay, verificar. El 19-sep esa cuenta salió 71 donde tocaba 70, y el usuario lo
 * resumió así: *"ahora yo soy el rápido haciendo cálculos, imagina lo que le va a tomar"*.
 * El app ya sabe la barra y ya sabe los discos: la cuenta la hace él.
 *
 * El inventario es el de `docs/equipo.md`, el mismo para los dos atletas: cuatro discos de
 * cada denominación, o sea **dos por lado**.
 */
object Plates {

    /** Los discos de la casa, de mayor a menor, en kg. */
    val HOME = listOf(20.0, 10.0, 5.0, 2.5, 1.25)

    /** Cuántos de cada uno caben por lado: hay cuatro de cada denominación. */
    const val PER_SIDE = 2

    /**
     * Los discos de UN lado para [platesTotal] kg de disco entre los dos lados, de mayor a
     * menor; lista vacía si es solo la barra. Null si esa carga no se puede armar con
     * [available] -un número que no reparte en discos existentes-, que para el player es
     * un aviso y para quien diseña, un error de la rutina.
     *
     * Voraz de mayor a menor: con estas denominaciones (cada una es múltiplo de la
     * siguiente o la dobla) el voraz da siempre la combinación de menos discos.
     */
    fun perSide(platesTotal: Double, available: List<Double> = HOME, perSide: Int = PER_SIDE): List<Double>? {
        if (platesTotal < 0.0) return null
        var resto = platesTotal / 2.0
        val out = mutableListOf<Double>()
        for (disco in available.sortedDescending()) {
            var usados = 0
            while (usados < perSide && resto >= disco - EPS) {
                out.add(disco)
                resto -= disco
                usados++
            }
        }
        return if (kotlin.math.abs(resto) < EPS) out else null
    }

    private const val EPS = 1e-6
}
