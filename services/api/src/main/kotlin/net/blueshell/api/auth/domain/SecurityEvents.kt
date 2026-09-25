package net.blueshell.api.auth.domain

import net.blueshell.api.auth.persistence.SecurityActorKind
import net.blueshell.api.auth.persistence.SecurityEvent
import net.blueshell.api.auth.persistence.SecurityEventKind
import net.blueshell.api.auth.persistence.SecurityEventRepository
import net.blueshell.api.security.Browser
import net.blueshell.api.security.CurrentBrowser
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.EmailJobs.SecurityNotificationAudience
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

/** Who a security event is attributed to. */
sealed interface SecurityActor {
    data class Person(
        val userId: Long,
    ) : SecurityActor

    data object System : SecurityActor

    data object Operator : SecurityActor
}

/**
 * The security log, and the notifications it sends.
 *
 * An event whose kind notifies sends the person a security notification carrying its own lock
 * link. The link is issued here, when the event is written, so the email states exactly one event
 * and its link locks exactly one account.
 */
@Service
class SecurityEvents(
    private val repository: SecurityEventRepository,
    private val users: UserService,
    private val tokens: RecoveryTokenFactory,
    private val jobs: JobQueue,
    private val clock: Clock,
) {
    @Transactional
    fun record(
        subjectId: Long,
        kind: SecurityEventKind,
        actor: SecurityActor = SecurityActor.Person(subjectId),
        note: String? = null,
        browser: Browser? = CurrentBrowser.get(),
        oldAddress: String? = null,
    ): SecurityEvent {
        val subject = users.findById(subjectId)
        val event =
            repository.save(
                SecurityEvent(
                    subject = subject,
                    actor = (actor as? SecurityActor.Person)?.let { users.findById(it.userId) },
                    actorKind =
                        when (actor) {
                            is SecurityActor.Person -> SecurityActorKind.PERSON
                            SecurityActor.System -> SecurityActorKind.SYSTEM
                            SecurityActor.Operator -> SecurityActorKind.OPERATOR
                        },
                    kind = kind,
                    note = note?.takeIf { it.isNotBlank() },
                    browserFamily = browser?.family,
                    browserPlatform = browser?.platform,
                    occurredAt = clock.instant(),
                ),
            )
        if (kind.notifies) notify(subject, event, oldAddress)
        if (kind.tellsAdministrators) tellAdministrators(event)
        return event
    }

    @Transactional(readOnly = true)
    fun of(
        subjectId: Long,
        pageable: Pageable,
    ): Page<SecurityEvent> = repository.findBySubjectNewestFirst(subjectId, pageable)

    /** Whether this sign-in comes from a browser the account has not used before, on an account that has signed in. */
    @Transactional(readOnly = true)
    fun isNewBrowser(
        subjectId: Long,
        browser: Browser,
    ): Boolean = repository.hasSignedIn(subjectId) && !repository.hasSignedInFrom(subjectId, browser.family, browser.platform)

    @Scheduled(cron = $$"${app.security-events.purge-cron:0 30 3 * * *}")
    @Transactional
    fun purgeExpired(): Int = repository.purgeOlderThan(clock.instant().minus(RETENTION))

    private fun notify(
        subject: User,
        event: SecurityEvent,
        oldAddress: String?,
    ) {
        val lockToken = tokens.issue(subject, TokenPurpose.ACCOUNT_LOCK, LOCK_LINK_TTL)
        jobs.runAsync(
            EmailJobs.SecurityNotification,
            EmailJobs.SecurityNotificationPayload(
                securityEventId = requireNotNull(event.id),
                audience = if (oldAddress == null) SecurityNotificationAudience.PERSON else SecurityNotificationAudience.OLD_ADDRESS,
                lockToken = lockToken,
                recipientEmail = oldAddress,
            ),
        )
    }

    private fun tellAdministrators(event: SecurityEvent) {
        users.findAdministrators().forEach { admin ->
            jobs.runAsync(
                EmailJobs.SecurityNotification,
                EmailJobs.SecurityNotificationPayload(
                    securityEventId = requireNotNull(event.id),
                    audience = EmailJobs.SecurityNotificationAudience.ADMINISTRATOR,
                    recipientUserId = admin.id,
                ),
            )
        }
    }

    companion object {
        val RETENTION: Duration = Duration.ofDays(365)
        val LOCK_LINK_TTL: Duration = Duration.ofHours(72)
    }
}
