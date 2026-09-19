package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los discos por lado, sobre el inventario real de la casa (TD-130). */
class PlatesTest {

    @Test
    fun `el caso del 19-sep - 65 de disco son 20 mas 10 mas 2_5 por lado`() {
        // Hip thrust de NIKO en su barra de 6: 71 kg de total, 65 de disco.
        assertEquals(listOf(20.0, 10.0, 2.5), Plates.perSide(65.0))
    }

    @Test
    fun `solo barra es una lista vacia, no un error`() {
        assertEquals(emptyList<Double>(), Plates.perSide(0.0))
    }

    @Test
    fun `los pesos de la rutina lumbar se arman`() {
        // Puente sobre la barra de 6: 6, 21 y 36 kg.
        assertEquals(listOf(5.0, 2.5), Plates.perSide(15.0))
        assertEquals(listOf(10.0, 5.0), Plates.perSide(30.0))
    }

    @Test
    fun `un peso que no reparte en discos existentes no se puede armar`() {
        // 70 sobre la barra de 6 son 64 de disco, 32 por lado: con discos de 1.25 hacia
        // arriba no hay forma. Es exactamente la cuenta que no le salia al usuario.
        assertNull(Plates.perSide(64.0))
    }

    @Test
    fun `solo hay dos discos de cada uno por lado`() {
        // 45 por lado pediria 20 + 20 + 5: dos de 20 si caben. 85 por lado no.
        assertEquals(listOf(20.0, 20.0, 5.0), Plates.perSide(90.0))
        assertNull(Plates.perSide(170.0))
    }

    @Test
    fun `cualquier multiplo de 2_5 hasta el maximo se puede armar`() {
        // Es lo que dice la tabla de saltos de docs/equipo.md.
        var total = 0.0
        while (total <= 155.0) {
            val lado = Plates.perSide(total)
            assertTrue("$total no se pudo armar", lado != null)
            assertEquals(total / 2.0, lado!!.sum(), 1e-6)
            total += 2.5
        }
    }
}
