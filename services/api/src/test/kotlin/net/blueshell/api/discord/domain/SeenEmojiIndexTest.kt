package net.blueshell.api.discord.domain

import net.blueshell.api.discord.persistence.SeenEmojiRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import java.time.Instant

class SeenEmojiIndexTest {
    private val noon = Instant.parse("2026-10-24T12:00:00Z")

    @Test
    fun `counts the emoji a gateway sighting reports, each message once`() {
        var report: (EmojiSighting) -> Unit = {}
        val index = seenEmojiIndex(sightings = { report = it })
        index.start()

        report(EmojiSighting("9001", "spoopykat", true, "m1", noon))
        report(EmojiSighting("9001", "spoopykat", true, "m1", noon))
        report(EmojiSighting("9001", "spoopykat", true, "m2", noon))

        val kat = index.seen().single()
        assertThat(listOf(kat.id, kat.name, kat.animated, kat.kind, kat.uses))
            .containsExactly("9001", "spoopykat", true, DiscordEmojiKind.SEEN, 2L)
        assertThat(index.isRunning).isTrue()
        index.stop()
        assertThat(index.isRunning).isFalse()
    }

    @Test
    fun `keeps the gateway going when a sighting cannot be counted`() {
        val down: SeenEmojiRepository = mock { on { recordMessage(any(), any()) } doThrow IllegalStateException("down") }
        var report: (EmojiSighting) -> Unit = {}
        seenEmojiIndex(down, sightings = { report = it }).start()

        assertThatCode { report(EmojiSighting("9001", "spoopykat", false, "m1", noon)) }.doesNotThrowAnyException()
    }

    @Test
    fun `starts without a bot`() {
        val index = seenEmojiIndex()
        index.start()

        assertThat(index.seen()).isEmpty()
    }

    @Test
    fun `finds every server emoji in a message's text, and nothing else`() {
        assertThat(emojiIn("Carve! <:spoopykat:111> <a:ShellyStar:657> :fire: 🎃 <@123>", "m1", noon)).containsExactly(
            EmojiSighting("111", "spoopykat", false, "m1", noon),
            EmojiSighting("657", "ShellyStar", true, "m1", noon),
        )
    }
}
