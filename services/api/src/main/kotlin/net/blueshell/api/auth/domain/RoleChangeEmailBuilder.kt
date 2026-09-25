package net.blueshell.api.auth.domain

import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.persistence.RoleChange
import net.blueshell.api.user.persistence.dormantGranted

/**
 * What somebody is told when their admin or board access changes.
 *
 * Those two roles reach here, and any role granted that waits on two-factor, so the mail names
 * access the person can go and use, a page that has stopped answering or the set-up that opens it.
 * The note the admin left is theirs, so it stays out.
 */
fun createRoleChangeEmail(
    change: RoleChange,
    frontendUrl: String,
): EmailContent {
    val recipient = change.subject
    val waiting = change.dormantGranted
    val gained = (NOTIFIED.filter { it in change.rolesAfter && it !in change.rolesBefore } + waiting).distinct()
    val lost = NOTIFIED.filter { it in change.rolesBefore && it !in change.rolesAfter }

    val markdownContent =
        buildList {
            add("Dear ${recipient.fullName},")
            add("")
            if (gained.isNotEmpty()) {
                add("You now hold ${naming(gained)} on the Blueshell website.")
                add("")
                add("The management pages are at [$frontendUrl]($frontendUrl), under your account menu.")
            }
            if (waiting.isNotEmpty()) {
                add("")
                add(
                    "It opens once your account has two-factor authentication. Every sign-in you had has ended, so " +
                        "sign in again at [$frontendUrl/login]($frontendUrl/login): the website takes you straight to " +
                        "setting it up, which takes about two minutes.",
                )
            }
            if (lost.isNotEmpty()) {
                if (gained.isNotEmpty()) add("")
                add("You no longer hold ${naming(lost)} on the Blueshell website.")
                add("")
                add("Pages that access opened are no longer there. Nothing is broken.")
            }
            add("")
            add("If this is a surprise, reply to this email and the board will look into it.")
            add("")
            add("Kind regards,")
            add("Board of ESA Blueshell")
        }.joinToString("\n")

    return EmailContent(
        recipientEmail = recipient.email,
        recipientName = recipient.fullName,
        subject = if (gained.isNotEmpty()) "Your Blueshell access has been extended" else "Your Blueshell access has changed",
        markdownContent = markdownContent,
    )
}

private val NOTIFIED = listOf(Role.ADMIN, Role.BOARD)

private fun naming(roles: List<Role>): String = roles.joinToString(" and ") { label(it) }

private fun label(role: Role): String =
    when (role) {
        Role.ADMIN -> "administrator access"
        Role.BOARD -> "board access"
        else -> "${role.name.lowercase()} access"
    }
