package app.ammusic.youtube.stream

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StreamResolverTest {
    private class FakeSource(override val name: String, private val fails: Boolean = false) : StreamSource {
        var calls = 0
        var rejected = 0

        override suspend fun resolve(videoId: String, quality: AudioQuality): StreamInfo {
            calls++
            if (fails) error("$name en panne")
            return StreamInfo("https://$name/$videoId/$calls", emptyMap(), expiresAtMs = 1_000_000L, source = name, client = name)
        }

        override suspend fun onRejected(videoId: String, stream: StreamInfo) {
            rejected++
        }
    }

    @Test
    fun `passe a la methode suivante quand la premiere echoue`() = runBlocking {
        val resolver = StreamResolver(listOf(FakeSource("a", fails = true), FakeSource("b")), now = { 0L })
        assertEquals("b", resolver.resolve("id").source)
    }

    @Test
    fun `reutilise l'URL tant qu'elle n'expire pas`() = runBlocking {
        var time = 0L
        val source = FakeSource("a")
        val resolver = StreamResolver(listOf(source), now = { time })
        resolver.resolve("id")
        resolver.resolve("id")
        assertEquals(1, source.calls)
        time = 950_000L // moins d'une minute avant l'expiration
        resolver.resolve("id")
        assertEquals(2, source.calls)
    }

    @Test
    fun `un refus du serveur previent la methode et force une nouvelle URL`() = runBlocking {
        val source = FakeSource("a")
        val resolver = StreamResolver(listOf(source), now = { 0L })
        val first = resolver.resolve("id")
        resolver.reject("id")
        assertEquals(1, source.rejected)
        assert(resolver.resolve("id").url != first.url)
    }

    @Test
    fun `echoue avec toutes les causes quand aucune methode ne marche`() {
        val resolver = StreamResolver(listOf(FakeSource("a", fails = true), FakeSource("b", fails = true)), now = { 0L })
        val error = assertThrows(StreamUnavailableException::class.java) { runBlocking { resolver.resolve("id") } }
        assertEquals(2, error.causes.size)
    }
}
