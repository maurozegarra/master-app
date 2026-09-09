package com.maurozegarra.master.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * El orden con que se decide el estado de un vídeo. Es lo unico delicado de esa regla, y
 * cada caso de aqui esta porque su contrario hace algo que el usuario no pidio.
 */
class VideoStateTest {

    private val file = File("own/ex_cat_cow.mp4")

    /** Si ganase el archivo, un video oculto se seguiria enseñando. */
    @Test
    fun `hidden wins over a downloaded file`() {
        assertEquals(VideoState.Hidden, videoStateOf(hidden = true, rev = 1, file = file))
    }

    /** Si ganase la revision, se descargaria uno que el usuario dijo que no queria ver. */
    @Test
    fun `hidden wins over a published revision not yet downloaded`() {
        assertEquals(VideoState.Hidden, videoStateOf(hidden = true, rev = 1, file = null))
        assertEquals(VideoState.Hidden, videoStateOf(hidden = true, rev = null, file = null))
    }

    @Test
    fun `a file that is there is the one that plays`() {
        assertEquals(VideoState.Ready(file), videoStateOf(hidden = false, rev = 1, file = file))
        assertEquals(VideoState.Ready(file), videoStateOf(hidden = false, rev = null, file = file))
    }

    @Test
    fun `published but not downloaded is pending, and nothing at all is none`() {
        assertEquals(VideoState.Pending, videoStateOf(hidden = false, rev = 2, file = null))
        assertEquals(VideoState.None, videoStateOf(hidden = false, rev = null, file = null))
    }
}
