package net.blueshell.api.discord.domain

import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class SeenEmojiIndexIT : ServiceTestSupport() {
    @Autowired
    private lateinit var index: SeenEmojiIndex

    private val noon = Instant.parse("2026-10-24T12:00:00Z")

    private fun sighting(
        emojiId: String,
        name: String,
        messageId: String,
        at: Instant = noon,
        animated: Boolean = false,
    ) = EmojiSighting(emojiId, name, animated, messageId, at)

    private fun seen(emojiId: String) = index.seen().single { it.id == emojiId }

    @Test
    fun `counts each message that used an emoji once, however often it is seen`() {
        index.saw(sighting("9001", "spoopykat", "m1"))
        index.saw(sighting("9001", "spoopykat", "m1"))
        index.saw(sighting("9001", "spoopykat", "m2"))

        val kat = seen("9001")
        assertThat(listOf(kat.name, kat.kind, kat.uses)).containsExactly("spoopykat", DiscordEmojiKind.SEEN, 2L)
    }

    @Test
    fun `follows the newest name, and keeps it when an older message is read later`() {
        index.saw(sighting("9002", "pumpkin", "m1"))
        index.saw(sighting("9002", "pumpkin_carved", "m2", at = noon.plusSeconds(60), animated = true))
        index.saw(sighting("9002", "pumpkin_old", "m0", at = noon.minusSeconds(60)))

        val pumpkin = seen("9002")
        assertThat(listOf(pumpkin.name, pumpkin.animated, pumpkin.uses)).containsExactly("pumpkin_carved", true, 3L)
    }

    @Test
    fun `lists the most used first`() {
        index.saw(sighting("9003", "rare", "m1"))
        index.saw(sighting("9004", "common", "m1"))
        index.saw(sighting("9004", "common", "m2"))

        assertThat(index.seen().map { it.id }).containsSubsequence("9004", "9003")
    }
}
