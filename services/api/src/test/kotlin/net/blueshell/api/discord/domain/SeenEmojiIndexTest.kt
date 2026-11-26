package net.blueshell.api.discord.domain

import net.blueshell.api.discord.persistence.SeenEmojiRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import java.time.Instant

class SeenEmojiIndexTest {
    private val noon = Instant.parse("2026-10-24T12:00:00Z")

    @Test
    fun `counts the emoji a gateway sighting reports, each message once, written before it stops`() {
        var report: (EmojiSighting) -> Unit = {}
        val index = seenEmojiIndex(sightings = { report = it })
        index.start()

        report(EmojiSighting("9001", "spoopykat", true, "m1", noon))
        report(EmojiSighting("9001", "spoopykat", true, "m1", noon))
        report(EmojiSighting("9001", "spoopykat", true, "m2", noon))
        assertThat(index.isRunning).isTrue()
        index.stop()

        val kat = index.seen().single()
        assertThat(listOf(kat.id, kat.name, kat.animated, kat.kind, kat.uses))
            .containsExactly("9001", "spoopykat", true, DiscordEmojiKind.SEEN, 2L)
        assertThat(index.isRunning).isFalse()
        assertThatCode { report(EmojiSighting("9001", "spoopykat", true, "m3", noon)) }.doesNotThrowAnyException()
    }

    @Test
    fun `writes a sighting off the gateway's thread, which a slow database would otherwise hold up`() {
        val writers = mutableListOf<String>()
        val repository =
            object : SeenEmojiRepository by HeldSeenEmoji() {
                override fun recordMessage(
                    emojiId: String,
                    messageId: String,
                ): Int = 1.also { writers += Thread.currentThread().name }
            }
        var report: (EmojiSighting) -> Unit = {}
        val index = seenEmojiIndex(repository, sightings = { report = it })
        index.start()

        report(EmojiSighting("9001", "spoopykat", false, "m1", noon))
        index.stop()

        assertThat(writers).containsExactly("discord-emoji")
    }

    @Test
    fun `keeps counting after a sighting that cannot be counted`() {
        val held = HeldSeenEmoji()
        var failing = true
        val flaky =
            object : SeenEmojiRepository by held {
                override fun recordMessage(
                    emojiId: String,
                    messageId: String,
                ): Int = if (failing.also { failing = false }) error("down") else held.recordMessage(emojiId, messageId)
            }
        var report: (EmojiSighting) -> Unit = {}
        val index = seenEmojiIndex(flaky, sightings = { report = it })
        index.start()

        assertThatCode { report(EmojiSighting("9001", "spoopykat", false, "m1", noon)) }.doesNotThrowAnyException()
        report(EmojiSighting("9001", "spoopykat", false, "m2", noon))
        index.stop()

        assertThat(index.seen().single().uses).isEqualTo(1L)
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
