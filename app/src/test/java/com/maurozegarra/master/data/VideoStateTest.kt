package com.maurozegarra.master.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** El orden con que se decide el estado de un video. */
class VideoStateTest {

    private val file = File("own/ex_cat_cow.mp4")

    /** El archivo que ya esta manda: uno propio gana al publicado, y uno bajado no se repide. */
    @Test
    fun `a file that is there is the one that plays`() {
        assertEquals(VideoState.Ready(file), videoStateOf(rev = 1, file = file))
        assertEquals(VideoState.Ready(file), videoStateOf(rev = null, file = file))
    }

    @Test
    fun `published but not downloaded is pending, and nothing at all is none`() {
        assertEquals(VideoState.Pending, videoStateOf(rev = 2, file = null))
        assertEquals(VideoState.None, videoStateOf(rev = null, file = null))
    }
}
