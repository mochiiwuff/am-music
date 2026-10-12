package app.ammusic.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverMatchTest {
    @Test
    fun `la mention du clip est retiree du titre`() {
        assertEquals(listOf(SongGuess("tous les mêmes", "Stromae")), songGuesses("tous les mêmes (Official Video)", "Stromae"))
        assertEquals("Basique", songOnly("Basique [CLIP OFFICIEL]"))
        assertEquals("Roi", songOnly("Roi ft. Adèle Castillon (Clip officiel)"))
    }

    @Test
    fun `un titre de la forme artiste - titre est separe`() {
        val guesses = songGuesses("Stromae - Papaoutai (Clip officiel)", "StromaeVEVO")
        assertEquals(SongGuess("Papaoutai", "Stromae"), guesses.first())
    }

    @Test
    fun `le meme titre du meme artiste correspond malgre les accents et la casse`() {
        val guess = songGuesses("tous les mêmes (Official Video)", "Stromae").first()
        assertTrue(sameSong("Tous les mêmes", "Stromae", guess))
        assertTrue(sameSong("Dommage", "Bigflo & Oli", SongGuess("Dommage", "Bigflo & Oli")))
        assertTrue(sameSong("Papaoutai", "Stromae", SongGuess("Papaoutai", "StromaeVEVO")))
    }

    @Test
    fun `un autre artiste ou une autre version ne correspond pas`() {
        assertFalse(sameSong("Dommage", "Un autre", SongGuess("Dommage", "Bigflo & Oli")))
        val live = songGuesses("Formidable (Live)", "Stromae").first()
        assertFalse(sameSong("Formidable", "Stromae", live))
        assertFalse(sameSong("Formidable", "", SongGuess("Formidable", "Stromae")))
    }
}
