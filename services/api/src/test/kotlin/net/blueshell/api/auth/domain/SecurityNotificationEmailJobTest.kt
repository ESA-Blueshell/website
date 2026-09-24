package net.blueshell.api.auth.domain

import net.blueshell.api.auth.persistence.SecurityActorKind
import net.blueshell.api.auth.persistence.SecurityEvent
import net.blueshell.api.auth.persistence.SecurityEventKind
import net.blueshell.api.auth.persistence.SecurityEventRepository
import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

class SecurityNotificationEmailJobTest {
    private val events = mock<SecurityEventRepository>()
    private val users = mock<UserService>()
    private val emails = mock<EmailSenderService>()
    private val mapper = JsonMapper.builder().findAndAddModules().build()
    private val job = SecurityNotificationEmailJob(mapper, events, users, emails, "https://site", SecurityContacts("board@example.org", "https://api/discord/channel/board", "https://api/discord/channel/suggestions"))

    private fun person(id: Long, email: String) =
        User(username = "u$id", email = email, password = "h", initials = "U", firstName = "U", lastName = "$id").also { it.id = id }

    private fun sent(payload: EmailJobs.SecurityNotificationPayload): EmailContent {
        val subject = person(7, "person@example.com")
        whenever(events.findWithPeopleById(99)).thenReturn(
            SecurityEvent(subject, subject, SecurityActorKind.PERSON, SecurityEventKind.PASSWORD_CHANGED, null, null, null, Instant.EPOCH),
        )
        whenever(users.findById(1)).thenReturn(person(1, "admin@example.com"))
        job.handle(mapper.writeValueAsString(payload), 5)
        val content = argumentCaptor<EmailContent>()
        verify(emails, atLeastOnce()).send(content.capture(), eq(EmailJobs.SecurityNotification.type), anyOrNull())
        return content.lastValue
    }

    @Test
    fun `a notice goes to the person, to the address they left, or to an admin`() {
        assertThat(job.jobType).isEqualTo("email.security-notification")
        assertThat(sent(EmailJobs.SecurityNotificationPayload(99, EmailJobs.SecurityNotificationAudience.PERSON, "s.v")).recipientEmail)
            .isEqualTo("person@example.com")
        assertThat(
            sent(
                EmailJobs.SecurityNotificationPayload(99, EmailJobs.SecurityNotificationAudience.OLD_ADDRESS, "s.v", "old@example.com"),
            ).recipientEmail,
        ).isEqualTo("old@example.com")
        val toAdmin = sent(EmailJobs.SecurityNotificationPayload(99, EmailJobs.SecurityNotificationAudience.ADMINISTRATOR, recipientUserId = 1))
        assertThat(toAdmin.recipientEmail).isEqualTo("admin@example.com")
        assertThat(toAdmin.markdownContent).contains("was locked")
    }

    @Test
    fun `two notices alike both go out`() {
        assertThat(
            EmailJobs.SecurityNotification.dedupKey(EmailJobs.SecurityNotificationPayload(99, EmailJobs.SecurityNotificationAudience.PERSON, "s.v")),
        ).isNull()
    }
}
