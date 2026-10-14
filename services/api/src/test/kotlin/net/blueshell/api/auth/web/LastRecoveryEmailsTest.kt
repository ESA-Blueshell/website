package net.blueshell.api.auth.web

import net.blueshell.api.auth.domain.LastRecoveryEmails
import net.blueshell.api.auth.domain.RecoveryUseCases
import net.blueshell.api.auth.persistence.LastRecoveryEmail
import net.blueshell.api.auth.persistence.RecoveryTokenRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

class LastRecoveryEmailsTest {
    private val tokens: RecoveryTokenRepository = mock()
    private val controller = RecoveryController(mock<RecoveryUseCases>(), LastRecoveryEmails(tokens))

    private fun sent(
        userId: Long,
        at: String,
    ) = object : LastRecoveryEmail {
        override val userId = userId
        override val sentAt: Instant = Instant.parse(at)
    }

    @Test
    fun `answers when each account was last sent an activation or a password reset`() {
        whenever(tokens.findLastIssuedPerUser(LastRecoveryEmails.HELPING_IN))
            .thenReturn(listOf(sent(9, "2026-09-01T10:00:00Z"), sent(3, "2026-08-01T10:00:00Z")))

        val answer = controller.lastRecoveryEmails()

        assertThat(answer.emails.map { it.userId }).containsExactly(3, 9)
        val mapper = JsonMapper.builder().findAndAddModules().build()
        assertThat(mapper.writeValueAsString(answer))
            .contains("\"userId\":3", "\"sentAt\":\"2026-08-01T10:00:00Z\"")
    }
}
