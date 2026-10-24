package net.blueshell.api.discord.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SeenEmojiTest {
    @Test
    fun `Hibernate can build a seen emoji to load a row into`() {
        // The constructor the JPA compiler plugin adds, which is the only one Hibernate calls.
        val loaded = SeenEmoji::class.java.getDeclaredConstructor().newInstance()

        assertThat(loaded.id as String?).isNull()
    }
}
