package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.auth.domain.AccountStanding
import net.blueshell.api.auth.persistence.SecurityActorKind
import net.blueshell.api.auth.persistence.SecurityEvent
import net.blueshell.api.auth.persistence.SecurityEventKind
import net.blueshell.api.auth.persistence.TrustedBrowser
import net.blueshell.api.security.SignIn
import org.springframework.data.domain.Page
import java.time.Instant

/** A secret being set up: the URI a QR code is drawn from, and the key to type by hand. */
@Schema(name = "TwoFactorSetupResponse")
data class TwoFactorSetupResponse(
    val otpauthUri: String,
    val key: String,
)

/** Ten backup codes, shown this once. */
@Schema(name = "BackupCodesResponse")
data class BackupCodesResponse(
    val codes: List<String>,
)

@Schema(name = "SignInResponse")
data class SignInResponse(
    val id: String,
    val browser: String,
    val platform: String,
    val signedInAt: Instant,
    val lastSeenAt: Instant,
    val current: Boolean,
)

fun SignIn.asResponse(currentId: String): SignInResponse =
    SignInResponse(id, browser.family, browser.platform, startedAt, lastSeenAt, id == currentId)

@Schema(name = "TrustedBrowserResponse")
data class TrustedBrowserResponse(
    val id: Long,
    val browser: String,
    val platform: String,
    val trustedAt: Instant,
    val expiresAt: Instant,
    val lastUsedAt: Instant?,
)

fun TrustedBrowser.asResponse(): TrustedBrowserResponse =
    TrustedBrowserResponse(requireNotNull(id), browserFamily, browserPlatform, trustedAt, expiresAt, lastUsedAt)

@Schema(name = "SecurityEventResponse")
data class SecurityEventResponse(
    val id: Long,
    val kind: SecurityEventKind,
    val actorKind: SecurityActorKind,
    /** Who acted, when it was somebody other than the person themselves. */
    val actorName: String?,
    val browser: String?,
    val note: String?,
    val occurredAt: Instant,
)

@Schema(name = "SecurityEventPageResponse")
data class SecurityEventPageResponse(
    val events: List<SecurityEventResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
)

fun Page<SecurityEvent>.asResponse(): SecurityEventPageResponse =
    SecurityEventPageResponse(
        events =
            content.map {
                SecurityEventResponse(
                    id = requireNotNull(it.id),
                    kind = it.kind,
                    actorKind = it.actorKind,
                    actorName = it.actor?.takeIf { actor -> actor.id != it.subject.id }?.fullName,
                    browser = it.browser,
                    note = it.note,
                    occurredAt = it.occurredAt,
                )
            },
        totalElements = totalElements,
        totalPages = totalPages,
        page = number,
    )

@Schema(name = "AccountStandingResponse")
data class AccountStandingResponse(
    val twoFactorOn: Boolean,
    val awaitingReenrolment: Boolean,
    val locked: Boolean,
)

fun AccountStanding.asResponse(): AccountStandingResponse = AccountStandingResponse(twoFactorOn, awaitingReenrolment, locked)

/** Who a locked-out person is told to contact. */
@Schema(name = "LockResponse")
data class LockResponse(
    val contactEmail: String,
)
