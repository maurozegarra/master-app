package com.maurozegarra.master.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class VideoCacheTest {

    private lateinit var dir: File
    private lateinit var cache: VideoCache

    @Before
    fun setUp() {
        dir = File.createTempFile("videocache", "").let {
            it.delete()
            it.mkdirs()
            it
        }
        cache = VideoCache(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun write(file: File, bytes: Int = 10) {
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(bytes))
    }

    @Test
    fun `nothing downloaded resolves to nothing`() {
        assertNull(cache.resolve("ex_cat_cow", rev = 1))
    }

    @Test
    fun `resolves the downloaded revision`() {
        write(cache.repoFile("ex_cat_cow", 1))

        assertEquals(cache.repoFile("ex_cat_cow", 1), cache.resolve("ex_cat_cow", rev = 1))
    }

    /** El usuario se molesto en asignar el suyo: publicar en el manifiesto no lo pisa. */
    @Test
    fun `the user's own video wins over the published one`() {
        write(cache.repoFile("ex_cat_cow", 1))
        write(cache.ownFile("ex_cat_cow"))

        assertEquals(cache.ownFile("ex_cat_cow"), cache.resolve("ex_cat_cow", rev = 1))
    }

    @Test
    fun `the own video is used even with nothing published`() {
        write(cache.ownFile("ex_cat_cow"))

        assertEquals(cache.ownFile("ex_cat_cow"), cache.resolve("ex_cat_cow", rev = null))
    }

    /** Una descarga que no llego a escribir nada no puede pasar por buena. */
    @Test
    fun `an empty file does not count as downloaded`() {
        write(cache.repoFile("ex_cat_cow", 1), bytes = 0)

        assertFalse(cache.hasRepoVideo("ex_cat_cow", 1))
        assertNull(cache.resolve("ex_cat_cow", rev = 1))
    }

    @Test
    fun `a new revision is a different file, so the old one is still there`() {
        write(cache.repoFile("ex_cat_cow", 1))

        assertFalse(cache.hasRepoVideo("ex_cat_cow", 2))
        assertTrue(cache.hasRepoVideo("ex_cat_cow", 1))
    }

    @Test
    fun `pruning keeps the given revision and drops the rest`() {
        write(cache.repoFile("ex_cat_cow", 1))
        write(cache.repoFile("ex_cat_cow", 2))
        write(cache.repoFile("ex_cat_cow", 3))

        cache.dropOtherRevisions("ex_cat_cow", keepRev = 3)

        assertTrue(cache.hasRepoVideo("ex_cat_cow", 3))
        assertFalse(cache.hasRepoVideo("ex_cat_cow", 1))
        assertFalse(cache.hasRepoVideo("ex_cat_cow", 2))
    }

    @Test
    fun `pruning one exercise leaves the others alone`() {
        write(cache.repoFile("ex_cat_cow", 1))
        write(cache.repoFile("ex_open_book", 1))

        cache.dropOtherRevisions("ex_cat_cow", keepRev = 2)

        assertTrue(cache.hasRepoVideo("ex_open_book", 1))
    }

    /** Estan en carpetas distintas justo para que la poda no pueda rozar el propio. */
    @Test
    fun `pruning never touches the user's own video`() {
        write(cache.ownFile("ex_cat_cow"))
        write(cache.repoFile("ex_cat_cow", 1))

        cache.dropOtherRevisions("ex_cat_cow", keepRev = 2)

        assertTrue(cache.hasOwnVideo("ex_cat_cow"))
    }

    @Test
    fun `clearing downloads keeps the user's own videos`() {
        write(cache.repoFile("ex_cat_cow", 1))
        write(cache.ownFile("ex_open_book"))

        cache.clearDownloaded()

        assertFalse(cache.hasRepoVideo("ex_cat_cow", 1))
        assertTrue(cache.hasOwnVideo("ex_open_book"))
    }

    @Test
    fun `reports the bytes used by both folders`() {
        write(cache.repoFile("ex_cat_cow", 1), bytes = 100)
        write(cache.ownFile("ex_open_book"), bytes = 40)

        assertEquals(140L, cache.bytesUsed())
    }

    /** El repositorio los necesita para dar estado a ejercicios que no salen publicados. */
    @Test
    fun `lists the exercises with an own video`() {
        write(cache.ownFile("ex_cat_cow"))
        write(cache.ownFile("ex_open_book"))
        write(cache.repoFile("ex_squat", 1))

        assertEquals(setOf("ex_cat_cow", "ex_open_book"), cache.ownExerciseIds().toSet())
    }

    @Test
    fun `lists nothing when there are no own videos`() {
        assertTrue(cache.ownExerciseIds().isEmpty())
    }
}
