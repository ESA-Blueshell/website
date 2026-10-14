package net.blueshell.api.platform.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ScrubbingPatternLayoutTest {
    private val context = LoggerContext()

    @Test
    fun `the line and the stack trace logback appends are both scrubbed`() {
        val layout =
            ScrubbingPatternLayout().apply {
                context = this@ScrubbingPatternLayoutTest.context
                pattern = "%msg%n"
                start()
            }
        val logger = context.getLogger("test")
        val failure = IllegalStateException("Duplicate entry 'ann@example.org-9999' for key 'uk_users_email_deleted_at'")
        val event = LoggingEvent("test", logger, Level.ERROR, "Refused {}", failure, arrayOf("NL91ABNA0417164300"))

        val line = layout.doLayout(event)

        assertThat(line).contains("Refused [iban]", "Duplicate entry '[value]' for key 'uk_users_email_deleted_at'")
        assertThat(line).doesNotContain("ann@example.org", "NL91ABNA0417164300")
    }
}
