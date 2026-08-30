package com.maurozegarra.master.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PREFIX = "master-autobackup-"

private fun name(stamp: String) = "$PREFIX$stamp.json"

class SnapshotRetentionTest {

    @Test
    fun `keeps everything while there is room`() {
        val names = listOf("2026-08-30T10-00", "2026-08-30T11-00").map(::name)

        val expired = SnapshotRetention.expired(names, PREFIX, keepRecent = 8, keepDays = 7)

        assertTrue(expired.isEmpty())
    }

    @Test
    fun `keeps the newest ones beyond the recent quota`() {
        val names = (1..12).map { name("2026-08-30T%02d-00".format(it)) }

        val expired = SnapshotRetention.expired(names, PREFIX, keepRecent = 3, keepDays = 0)

        assertEquals(listOf(9, 8, 7, 6, 5, 4, 3, 2, 1).map { name("2026-08-30T%02d-00".format(it)) }, expired)
    }

    /**
     * El caso que motivó TD-060: un día con muchos snapshots no puede llevarse por
     * delante la historia anterior, porque el estado bueno puede ser el de ayer.
     */
    @Test
    fun `a busy day does not evict older days`() {
        val today = (1..10).map { name("2026-08-30T%02d-00".format(it)) }
        val earlier = listOf("2026-08-29T09-00", "2026-08-28T09-00", "2026-08-27T09-00").map(::name)

        val expired = SnapshotRetention.expired(today + earlier, PREFIX, keepRecent = 3, keepDays = 7)

        assertTrue(expired.none { it in earlier })
    }

    @Test
    fun `keeps only the newest snapshot of each old day`() {
        val names = listOf(
            "2026-08-29T09-00", "2026-08-29T21-00",
            "2026-08-28T08-00", "2026-08-28T22-00",
        ).map(::name)

        val expired = SnapshotRetention.expired(names, PREFIX, keepRecent = 0, keepDays = 7)

        assertEquals(listOf(name("2026-08-29T09-00"), name("2026-08-28T08-00")).sortedDescending(), expired)
    }

    @Test
    fun `drops days beyond the day quota`() {
        val names = (20..28).map { name("2026-08-%02dT09-00".format(it)) }

        val expired = SnapshotRetention.expired(names, PREFIX, keepRecent = 0, keepDays = 3)

        assertEquals(listOf(25, 24, 23, 22, 21, 20).map { name("2026-08-%02dT09-00".format(it)) }, expired)
    }

    /** Los snapshots del formato viejo, sin hora, se podan junto a los nuevos. */
    @Test
    fun `legacy daily names still group by day`() {
        val names = listOf(name("2026-08-30"), name("2026-08-30T14-00"), name("2026-08-20"))

        val expired = SnapshotRetention.expired(names, PREFIX, keepRecent = 0, keepDays = 1)

        // Del 30 sobrevive el más nuevo, que es el que lleva hora; el 20 queda fuera de cuota.
        assertEquals(listOf(name("2026-08-30"), name("2026-08-20")), expired)
    }
}
