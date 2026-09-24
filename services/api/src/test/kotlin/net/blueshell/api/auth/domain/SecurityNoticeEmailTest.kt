package net.blueshell.api.auth.domain

import net.blueshell.api.auth.persistence.SecurityActorKind
import net.blueshell.api.auth.persistence.SecurityEvent
import net.blueshell.api.auth.persistence.SecurityEventKind
import net.blueshell.api.shared.job.EmailJobs.SecurityNoticeAudience
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant

class SecurityNoticeEmailTest {
    private val contacts = SecurityContacts(
        "board@example.org",
        "https://api/discord/channel/board",
        "https://api/discord/channel/suggestions",
    )

    private fun person(id: Long) =
        User(username = "alice", email = "alice@example.com", password = "h", initials = "A", firstName = "Alice", lastName = "Doe").also {
            it.id = id
        }

    private fun event(
        kind: SecurityEventKind,
        actor: User? = null,
        actorKind: SecurityActorKind = SecurityActorKind.PERSON,
    ): SecurityEvent {
        val subject = person(7)
        return SecurityEvent(subject, actor ?: subject, actorKind, kind, "why", "Firefox on Linux", Instant.parse("2026-09-24T12:00:00Z"))
    }

    private fun email(
        event: SecurityEvent,
        audience: SecurityNoticeAudience = SecurityNoticeAudience.PERSON,
        lockToken: String? = "sel.ver",
    ) = createSecurityNoticeEmail(event, audience, "to@example.com", "Alice Doe", lockToken, "https://site", contacts)

    @ParameterizedTest
    @EnumSource(SecurityEventKind::class)
    fun `every kind reads as a sentence, and a person's notice carries the lock link and who to ask`(kind: SecurityEventKind) {
        val sent = email(event(kind))

        assertThat(sent.recipientEmail).isEqualTo("to@example.com")
        assertThat(sent.subject).isEqualTo("Security notice for your Blueshell account")
        assertThat(sent.markdownContent).contains("on 24 September 2026 at 14:00 from Firefox on Linux.")
        assertThat(sent.markdownContent).contains("https://site/account/lock#token=sel.ver")
        assertThat(sent.markdownContent).contains("[board@example.org](mailto:board@example.org)")
        assertThat(sent.markdownContent).contains("[#board-questions](https://api/discord/channel/board)")
    }

    @Test
    fun `a change somebody else made reads that way, and without a link there is nothing to lock`() {
        val byAdmin = event(SecurityEventKind.SIGNED_IN, actor = person(1))
        val bySystem = event(SecurityEventKind.SIGNED_IN, actorKind = SecurityActorKind.SYSTEM)

        assertThat(email(byAdmin, lockToken = null).markdownContent)
            .contains("Something changed about how your account is signed in to")
            .doesNotContain("lock your account")
        assertThat(email(bySystem).markdownContent).contains("Something changed")
        assertThat(email(event(SecurityEventKind.SIGNED_IN)).markdownContent).contains("You changed how your account")
    }

    @Test
    fun `an admin is told of a lock and of a break-glass run`() {
        val locked = email(event(SecurityEventKind.ACCOUNT_LOCKED), SecurityNoticeAudience.ADMINISTRATOR, null)
        val glass = email(
            event(SecurityEventKind.BREAK_GLASS, actorKind = SecurityActorKind.OPERATOR),
            SecurityNoticeAudience.ADMINISTRATOR,
            null,
        )

        assertThat(locked.subject).isEqualTo("A Blueshell account was locked")
        assertThat(locked.markdownContent).contains("The account of Alice Doe (alice) was locked")
        assertThat(glass.subject).isEqualTo("The break-glass command was used on a Blueshell account")
        assertThat(glass.markdownContent).contains("on 24 September 2026 at 14:00: why.")
    }

    @Test
    fun `an event with no browser says none`() {
        val subject = person(7)
        val quiet = SecurityEvent(subject, subject, SecurityActorKind.PERSON, SecurityEventKind.PASSWORD_RESET, null, null, Instant.EPOCH)

        assertThat(email(quiet).markdownContent).contains("Your password was reset through the emailed link on 1 January 1970 at 01:00.")
    }
}
