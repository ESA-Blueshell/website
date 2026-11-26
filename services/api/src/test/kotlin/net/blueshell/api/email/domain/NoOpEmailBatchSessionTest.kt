package net.blueshell.api.email.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NoOpEmailBatchSessionTest {
    @Test
    fun `the block runs as is`() {
        assertThat(NoOpEmailBatchSession().open { "sent" }).isEqualTo("sent")
    }
}
