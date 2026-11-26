package net.blueshell.api.discord.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SeenEmojiBackfillPlaceTest {
    @Test
    fun `Hibernate can build a backfill place to load a row into`() {
        // The constructor the JPA compiler plugin adds, which is the only one Hibernate calls.
        val loaded = SeenEmojiBackfillPlace::class.java.getDeclaredConstructor().newInstance()

        assertThat(loaded.id as String?).isNull()
    }
}
