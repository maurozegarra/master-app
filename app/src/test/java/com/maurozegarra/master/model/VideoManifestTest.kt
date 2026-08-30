package com.maurozegarra.master.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val VALID = """
{
  "format": 1,
  "baseUrl": "https://github.com/maurozegarra/master-app/releases/download/videos/",
  "videos": {
    "ex_cat_cow": { "file": "ex_cat_cow.mp4", "rev": 2, "bytes": 1721463 },
    "ex_open_book": { "file": "ex_open_book.mp4" }
  }
}
"""

class VideoManifestTest {

    @Test
    fun `reads entries with their revision and size`() {
        val m = VideoManifestJson.decode(VALID)!!

        assertEquals(VideoEntry("ex_cat_cow.mp4", rev = 2, bytes = 1721463L), m.entry("ex_cat_cow"))
    }

    @Test
    fun `revision defaults to one and size to unknown`() {
        val m = VideoManifestJson.decode(VALID)!!

        assertEquals(VideoEntry("ex_open_book.mp4", rev = 1, bytes = 0L), m.entry("ex_open_book"))
    }

    @Test
    fun `builds the url joining base and file`() {
        val m = VideoManifestJson.decode(VALID)!!

        assertEquals(
            "https://github.com/maurozegarra/master-app/releases/download/videos/ex_cat_cow.mp4",
            m.urlFor("ex_cat_cow"),
        )
    }

    /** El manifiesto se edita a mano: la barra final no puede cambiar el resultado. */
    @Test
    fun `base url without trailing slash builds the same url`() {
        val m = VideoManifestJson.decode(VALID.replace("videos/\"", "videos\""))!!

        assertEquals(
            "https://github.com/maurozegarra/master-app/releases/download/videos/ex_cat_cow.mp4",
            m.urlFor("ex_cat_cow"),
        )
    }

    @Test
    fun `unknown exercise has no video`() {
        val m = VideoManifestJson.decode(VALID)!!

        assertNull(m.urlFor("ex_squat"))
    }

    /**
     * Al reves que en BackupJson: una entrada rota solo deja sin video a ese ejercicio,
     * asi que no puede tumbar el manifiesto entero.
     */
    @Test
    fun `a broken entry is dropped and the rest survives`() {
        val json = """
        {
          "format": 1,
          "baseUrl": "https://example.com/v/",
          "videos": {
            "ex_cat_cow": { "file": "ex_cat_cow.mp4" },
            "ex_broken": { "rev": 3 },
            "ex_also_broken": "no soy un objeto"
          }
        }
        """

        val m = VideoManifestJson.decode(json)!!

        assertEquals(setOf("ex_cat_cow"), m.videos.keys)
    }

    @Test
    fun `rejects garbage`() {
        assertNull(VideoManifestJson.decode("no soy json"))
    }

    @Test
    fun `rejects a format from the future`() {
        assertNull(VideoManifestJson.decode(VALID.replace("\"format\": 1", "\"format\": 99")))
    }

    @Test
    fun `rejects a manifest without base url`() {
        val json = """{ "format": 1, "videos": { "ex_cat_cow": { "file": "a.mp4" } } }"""

        assertNull(VideoManifestJson.decode(json))
    }

    @Test
    fun `an empty catalog is valid`() {
        val m = VideoManifestJson.decode("""{ "format": 1, "baseUrl": "https://e.com/", "videos": {} }""")!!

        assertTrue(m.videos.isEmpty())
    }
}
