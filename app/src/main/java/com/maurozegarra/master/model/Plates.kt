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
 * cada denominación —**dos por lado**— salvo los de 15, que son un par: **uno por lado**.
 */
object Plates {

    /** Los discos de la casa, de mayor a menor, en kg. */
    val HOME = listOf(20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    /** Cuántos de cada uno caben por lado: cuatro de cada denominación, dos por lado. */
    const val PER_SIDE = 2

    /**
     * Las excepciones a [PER_SIDE]. De 15 hay un solo par (22-sep-2026), o sea uno por lado.
     *
     * Van aquí y no en el cálculo porque el inventario crece de a poco y lo que cambia es
     * esta tabla, no el algoritmo.
     */
    val PER_SIDE_LIMIT = mapOf(15.0 to 1)

    /**
     * Los discos de UN lado para [platesTotal] kg de disco entre los dos lados, de mayor a
     * menor; lista vacía si es solo la barra. Null si esa carga no se puede armar con
     * [available] -un número que no reparte en discos existentes-, que para el player es
     * un aviso y para quien diseña, un error de la rutina.
     *
     * Voraz de mayor a menor. Con el disco de 15 en medio ya no es cierto que cada
     * denominación sea múltiplo de la siguiente, que era lo que garantizaba el voraz sobre
     * el papel; lo que lo garantiza ahora es el test, que barre TODO el rango de 1.25 en
     * 1.25 y exige que no quede ningún hueco.
     */
    fun perSide(platesTotal: Double, available: List<Double> = HOME, perSide: Int = PER_SIDE): List<Double>? {
        if (platesTotal < 0.0) return null
        var resto = platesTotal / 2.0
        val out = mutableListOf<Double>()
        for (disco in available.sortedDescending()) {
            var usados = 0
            val tope = minOf(perSide, PER_SIDE_LIMIT[disco] ?: perSide)
            while (usados < tope && resto >= disco - EPS) {
                out.add(disco)
                resto -= disco
                usados++
            }
        }
        return if (kotlin.math.abs(resto) < EPS) out else null
    }

    private const val EPS = 1e-6
}
