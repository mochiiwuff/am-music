package app.ammusic.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaylistEditTest {
    @Test
    fun `un ajout accepté donne l'identifiant du titre dans la playlist`() {
        val body = """{"status":"STATUS_SUCCEEDED","playlistEditResults":[{"playlistEditVideoAddedResultData":{"videoId":"abc","setVideoId":"56B44F6D10557CC6"}}]}"""
        assertEquals("56B44F6D10557CC6", playlistEditSetVideoId(body))
    }

    @Test
    fun `une réponse acceptée sans identifiant reste un succès`() {
        assertNull(playlistEditSetVideoId("""{"status": "STATUS_SUCCEEDED"}"""))
    }

    @Test
    fun `un refus de YouTube est une erreur`() {
        assertThrows(IllegalStateException::class.java) { playlistEditSetVideoId("""{"status":"STATUS_FAILED"}""") }
    }
}
