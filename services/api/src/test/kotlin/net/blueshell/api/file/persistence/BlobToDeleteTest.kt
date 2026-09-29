package net.blueshell.api.file.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BlobToDeleteTest {
    @Test
    fun `Hibernate can build a queued path to load a row into`() {
        // The constructor the JPA compiler plugin adds, which is the only one Hibernate calls.
        val loaded = BlobToDelete::class.java.getDeclaredConstructor().newInstance()

        assertThat(loaded.id).isNull()
    }
}
