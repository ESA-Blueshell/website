package net.blueshell.api.discord.persistence

import net.blueshell.api.shared.model.Identifiable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SeenEmojiTest {
    @Test
    fun `is identified by its emoji ID`() {
        val noon = Instant.parse("2026-10-24T12:00:00Z")
        val seen: Identifiable<String> = SeenEmoji("9001", "spoopykat", false, noon, noon, 1)

        assertThat(seen.id).isEqualTo("9001")
    }
}
