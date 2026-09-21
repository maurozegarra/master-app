package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los vídeos publicados, leídos de `exercise_media` (TD-141).
 *
 * Sustituye a `VideoManifestTest`: el manifiesto de GitHub se retiró cuando publicar pasó a
 * hacerse desde el teléfono. Lo que se protege es lo mismo de siempre —una fila rota no
 * puede dejar sin vídeo a los demás— más lo nuevo: que el nombre del archivo se arme igual
 * que en la caché, que es lo que evita volver a descargar lo que ya está.
 */
class PublishedVideosTest {

    private val base = "https://x.supabase.co/storage/v1/object/public/videos/"

    @Test
    fun `una fila con revision se lee entera`() {
        val filas = """[{"exercise_id":"ex_cat_cow","video_rev":2,"video_bytes":10485069}]"""

        val e = PublishedVideos.parse(filas, base).getValue("ex_cat_cow")

        assertEquals(2, e.rev)
        assertEquals(10485069L, e.bytes)
        // El mismo nombre que usa VideoCache.repoFile: <id>.<rev>.mp4.
        assertEquals("ex_cat_cow.2.mp4", e.file)
        assertEquals(base + "ex_cat_cow.2.mp4", e.url)
    }

    @Test
    fun `una fila sin video no entra`() {
        // Casi todas las filas son asi: tienen instrucciones y todavia no tienen video.
        val filas = """[{"exercise_id":"ex_hip_hinge","instructions":["Uno"]}]"""

        assertTrue(PublishedVideos.parse(filas, base).isEmpty())
    }

    @Test
    fun `una fila rota se descarta sola, las demas se quedan`() {
        val filas = """[{"video_rev":1},{"exercise_id":"ex_walk","video_rev":1,"video_bytes":10}]"""

        assertEquals(setOf("ex_walk"), PublishedVideos.parse(filas, base).keys)
    }

    @Test
    fun `una respuesta que no es json no rompe nada`() {
        assertTrue(PublishedVideos.parse("esto no es json", base).isEmpty())
    }

    @Test
    fun `sin bytes se descarga igual, solo se pierde la comprobacion de tamano`() {
        val filas = """[{"exercise_id":"ex_walk","video_rev":1}]"""

        assertEquals(0L, PublishedVideos.parse(filas, base).getValue("ex_walk").bytes)
        assertNull(PublishedVideos.parse(filas, base)["ex_otro"])
    }
}
