package net.blueshell.api.auth.domain

import net.blueshell.api.auth.persistence.SecurityActorKind
import net.blueshell.api.auth.persistence.SecurityEvent
import net.blueshell.api.auth.persistence.SecurityEventKind
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.job.EmailJobs.SecurityNotificationAudience
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What a security notification says about one event, to the person or to an admin. */
fun createSecurityNotificationEmail(
    event: SecurityEvent,
    audience: SecurityNotificationAudience,
    recipientEmail: String,
    recipientName: String,
    lockToken: String?,
    frontendUrl: String,
    contacts: SecurityContacts,
): EmailContent {
    val message =
        when {
            audience != SecurityNotificationAudience.ADMINISTRATOR -> toPerson(event, lockToken, frontendUrl, contacts)
            event.kind == SecurityEventKind.BREAK_GLASS -> breakGlassToAdministrator(event)
            else -> lockToAdministrator(event)
        }
    val body = listOf("Dear $recipientName,", "") + message.lines + listOf("", "Kind regards,", "Board of ESA Blueshell")
    return EmailContent(
        recipientEmail = recipientEmail,
        recipientName = recipientName,
        subject = message.subject,
        markdownContent = body.joinToString("\n"),
    )
}

private class Message(
    val subject: String,
    val lines: List<String>,
)

private fun toPerson(
    event: SecurityEvent,
    lockToken: String?,
    frontendUrl: String,
    contacts: SecurityContacts,
): Message {
    val lines = mutableListOf("${sentence(event)} on ${whenAndWhere(event)}.")
    if (lockToken != null) {
        val link = "$frontendUrl/account/lock#token=${URLEncoder.encode(lockToken, StandardCharsets.UTF_8)}"
        lines += ""
        lines += "If this was you, there is nothing to do."
        lines += ""
        lines += "If it was not, [lock your account]($link) now. Locking signs it out everywhere and"
        lines += "stops anybody signing in to it; it does not undo this change. The link works once, for"
        lines += "${SecurityEvents.LOCK_LINK_TTL.toHours()} hours. Then ${contacts.markdown} and an admin will help you back in."
    }
    return Message("Security notification for your Blueshell account", lines)
}

private fun breakGlassToAdministrator(event: SecurityEvent) =
    Message(
        "The break-glass command was used on a Blueshell account",
        listOf(
            "An operator used the break-glass command on the account of ${event.subject.fullName} (${event.subject.username})",
            "on ${WHEN.format(event.occurredAt)}: ${event.note}.",
            "",
            "If no admin asked for this, find out who ran it before anything else.",
        ),
    )

private fun lockToAdministrator(event: SecurityEvent) =
    Message(
        "A Blueshell account was locked",
        listOf(
            "The account of ${event.subject.fullName} (${event.subject.username}) was locked on ${whenAndWhere(event)}.",
            "",
            "Nobody can sign in to it until an admin unlocks it from the user manager. Before you do,",
            "hear from the person themselves, somewhere other than the account's own email.",
        ),
    )

private fun whenAndWhere(event: SecurityEvent): String {
    val browser = event.browserFamily?.let { "$it on ${event.browserPlatform}" }
    return WHEN.format(event.occurredAt) + browser?.let { " from $it" }.orEmpty()
}

private fun sentence(event: SecurityEvent): String {
    val byOther = event.actorKind != SecurityActorKind.PERSON || event.actor?.id != event.subject.id
    return when (event.kind) {
        SecurityEventKind.NEW_BROWSER -> "Your account was signed in to from a browser it has not used before"
        SecurityEventKind.SIGN_IN_REUSED -> "An old copy of your sign-in cookie was used, so that sign-in was ended"
        SecurityEventKind.SIGN_IN_BROWSER_CHANGED -> "Your sign-in turned up in another browser, so it was ended"
        SecurityEventKind.CODE_LIMIT_REACHED ->
            "Ten wrong two-factor codes were entered for your account. Whoever entered them knows your password"
        SecurityEventKind.PASSWORD_RESET -> "Your password was reset through the emailed link"
        SecurityEventKind.PASSWORD_CHANGED -> "Your password was changed"
        SecurityEventKind.EMAIL_CHANGE_REQUESTED -> "Somebody asked to move your account to another email address"
        SecurityEventKind.EMAIL_CHANGED_BY_BOARD -> "The board changed the email address of your account"
        SecurityEventKind.TWO_FACTOR_ON -> "Two-factor authentication was turned on"
        SecurityEventKind.TWO_FACTOR_OFF -> "Two-factor authentication was turned off"
        SecurityEventKind.TWO_FACTOR_REPLACED -> "Your authenticator app was replaced, with new backup codes"
        SecurityEventKind.BACKUP_CODES_REGENERATED -> "New backup codes were made, and the old ones stopped working"
        SecurityEventKind.BACKUP_CODE_USED -> "A backup code was used to sign in"
        SecurityEventKind.TRUSTED_BROWSER_ADDED -> "A browser was trusted to skip the code at sign-in"
        SecurityEventKind.TWO_FACTOR_RESET -> "An admin reset your two-factor authentication and signed you out everywhere"
        SecurityEventKind.ACCOUNT_UNLOCKED -> "An admin unlocked your account"
        else -> if (byOther) "Something changed about how your account is signed in to" else "You changed how your account is signed in to"
    }
}

private val WHEN: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy 'at' HH:mm").withZone(ZoneId.of("Europe/Amsterdam"))
