package net.blueshell.api.platform.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ScrubbingPatternLayoutTest {
    private val context = LoggerContext()
    private val logger = context.getLogger("test")
    private val layout =
        ScrubbingPatternLayout().apply {
            context = this@ScrubbingPatternLayoutTest.context
            pattern = "%msg%n"
            start()
        }

    @Test
    fun `the line and the stack trace logback appends are both scrubbed`() {
        val failure = IllegalStateException("Duplicate entry 'ann@example.org-9999' for key 'uk_users_email_deleted_at'")
        val event = LoggingEvent("test", logger, Level.ERROR, "Refused {}", failure, arrayOf("NL91ABNA0417164300"))

        val line = layout.doLayout(event)

        assertThat(line).contains("Refused [iban]", "Duplicate entry '[value]' for key 'uk_users_email_deleted_at'")
        assertThat(line).doesNotContain("ann@example.org", "NL91ABNA0417164300")
    }

    @Test
    fun `a password in a connection url is masked`() {
        val url = "jdbc:mariadb://mariadb.data-system.svc.cluster.local/blueshell?user=v-api-0P9a&password=pWnIZ3oY-xyb&useSSL=false"
        val event = LoggingEvent("test", logger, Level.INFO, "Database JDBC URL [{}]", null, arrayOf(url))

        val line = layout.doLayout(event)

        assertThat(line).contains("user=v-api-0P9a&password=[secret]&useSSL=false")
        assertThat(line).doesNotContain("pWnIZ3oY-xyb")
    }
}
