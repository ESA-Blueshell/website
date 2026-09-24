package net.blueshell.api.auth.domain

import net.blueshell.api.auth.persistence.SecurityActorKind
import net.blueshell.api.auth.persistence.SecurityEvent
import net.blueshell.api.auth.persistence.SecurityEventKind
import net.blueshell.api.auth.persistence.SecurityEventRepository
import net.blueshell.api.platform.config.SettableClock
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.time.Duration
import java.time.Instant

class SecurityEventsTest {
    private val clock = SettableClock().apply { set(Instant.parse("2026-09-24T12:00:00Z")) }
    private val repository = mock<SecurityEventRepository>()
    private val users = mock<UserService>()
    private val tokens = mock<RecoveryTokenFactory>()
    private val jobs = mock<JobQueue>()
    private val events = SecurityEvents(repository, users, tokens, jobs, clock)

    private fun person(id: Long) =
        User(username = "u$id", email = "u$id@example.com", password = "h", initials = "U", firstName = "U", lastName = "$id").also {
            it.id = id
        }

    private val subject = person(7)
    private val admin = person(1)

    @BeforeEach
    fun setUp() {
        whenever(users.findById(7)).thenReturn(subject)
        whenever(users.findById(1)).thenReturn(admin)
        whenever(repository.save(any<SecurityEvent>())).thenAnswer { (it.arguments[0] as SecurityEvent).also { e -> e.id = 99 } }
        whenever(tokens.issue(any(), any(), any())).thenReturn("sel.ver")
    }

    @Test
    fun `a notifying event is written, and the person is mailed a lock link of their own`() {
        val event = events.record(7, SecurityEventKind.PASSWORD_CHANGED, note = " ", browser = "Firefox on Linux")

        assertThat(event.subject).isSameAs(subject)
        assertThat(event.actor).isSameAs(subject)
        assertThat(event.actorKind).isEqualTo(SecurityActorKind.PERSON)
        assertThat(event.note).isNull()
        assertThat(event.browser).isEqualTo("Firefox on Linux")
        assertThat(event.occurredAt).isEqualTo(clock.instant())
        verify(tokens).issue(subject, TokenPurpose.ACCOUNT_LOCK, SecurityEvents.LOCK_LINK_TTL)
        verify(jobs).runAsync(
            EmailJobs.SecurityNotice,
            EmailJobs.SecurityNoticePayload(99, EmailJobs.SecurityNoticeAudience.PERSON, lockToken = "sel.ver"),
        )
    }

    @Test
    fun `a change of address is told to the address it leaves`() {
        events.record(7, SecurityEventKind.EMAIL_CHANGED_BY_BOARD, SecurityActor.Person(1), oldAddress = "old@example.com")

        verify(jobs).runAsync(
            EmailJobs.SecurityNotice,
            EmailJobs.SecurityNoticePayload(99, EmailJobs.SecurityNoticeAudience.OLD_ADDRESS, "sel.ver", "old@example.com"),
        )
    }

    @Test
    fun `a quiet event mails nobody, and the system and the operator are named as such`() {
        assertThat(events.record(7, SecurityEventKind.SIGNED_IN, SecurityActor.System).actorKind).isEqualTo(SecurityActorKind.SYSTEM)
        assertThat(events.record(7, SecurityEventKind.REENROLLED, SecurityActor.Operator).actor).isNull()

        verifyNoInteractions(jobs)
        verify(tokens, never()).issue(any(), any(), any())
    }

    @Test
    fun `a lock and a break-glass run tell every admin`() {
        whenever(users.findAdministrators()).thenReturn(listOf(admin))

        events.record(7, SecurityEventKind.ACCOUNT_LOCKED)
        events.record(7, SecurityEventKind.BREAK_GLASS, SecurityActor.Operator)

        val payloads = argumentCaptor<EmailJobs.SecurityNoticePayload>()
        verify(jobs, times(2)).runAsync(eq(EmailJobs.SecurityNotice), payloads.capture())
        assertThat(payloads.allValues.map { it.audience }).containsOnly(EmailJobs.SecurityNoticeAudience.ADMINISTRATOR)
        assertThat(payloads.allValues.map { it.recipientUserId }).containsOnly(1L)
    }

    @Test
    fun `the log is read a page at a time, and what is older than twelve months goes`() {
        whenever(repository.findBySubjectNewestFirst(7, Pageable.unpaged())).thenReturn(PageImpl(emptyList()))
        whenever(repository.hasSignedInFrom(7, "Firefox on Linux")).thenReturn(true)
        whenever(repository.purgeOlderThan(clock.instant().minus(Duration.ofDays(365)))).thenReturn(3)

        assertThat(events.of(7, Pageable.unpaged())).isEmpty()
        assertThat(events.hasSignedInFrom(7, "Firefox on Linux")).isTrue()
        assertThat(events.purgeExpired()).isEqualTo(3)
    }
}
