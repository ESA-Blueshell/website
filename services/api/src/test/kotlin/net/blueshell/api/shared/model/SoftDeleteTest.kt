package net.blueshell.api.shared.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SoftDeleteTest {
    @Test
    fun `the live sentinel reads the same as text, as a predicate and as an instant`() {
        assertThat(SoftDelete.ACTIVE).isEqualTo("deleted_at = '9999-12-31 23:59:59'")
        assertThat(SoftDelete.LIVE_INSTANT).isEqualTo(Instant.parse("9999-12-31T23:59:59Z"))
    }
}
