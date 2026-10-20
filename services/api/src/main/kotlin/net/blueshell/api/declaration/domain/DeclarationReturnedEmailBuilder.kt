package net.blueshell.api.declaration.domain

import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.user.api.MaskedIban
import net.blueshell.api.user.persistence.User
import java.util.Locale

/** The board's reason for sending a declaration back or rejecting it, quoted to its submitter. */
fun declarationReturnedEmail(
    recipient: User,
    payload: DeclarationJobs.DeclarationReturnedPayload,
    frontendUrl: String,
): EmailContent {
    val named = named(payload.title, payload.totalCents)
    val sentBack = payload.outcome == DeclarationJobs.Outcome.SENT_BACK
    val markdownContent =
        buildList {
            add("Dear ${recipient.fullName},")
            add("")
            add("The board ${if (sentBack) "sent back" else "rejected"} your declaration $named. Their reason:")
            add("")
            addAll(payload.reason.lines().map { "> $it" })
            add("")
            add(
                if (sentBack) {
                    "Your declaration is a draft again. Change it and submit it again on " +
                        "[your declaration page]($frontendUrl/account/declarations/${payload.declarationId})."
                } else {
                    "This decision is final. You cannot submit this declaration again. If you have a question, reply to this email. " +
                        "Your reply goes to the board."
                },
            )
            add("")
            add("Kind regards,")
            add("The board of ESA Blueshell")
        }.joinToString("\n")
    return EmailContent(
        recipientEmail = recipient.email,
        recipientName = recipient.fullName,
        subject = if (sentBack) "Your declaration needs changes" else "Your declaration is rejected",
        markdownContent = markdownContent,
        replyToOverride = BOARD,
    )
}

/** Tells the submitter the board paid their declaration, and to which account, masked. */
fun declarationPaidEmail(
    recipient: User,
    payload: DeclarationJobs.DeclarationPaidPayload,
): EmailContent {
    val account = requireNotNull(MaskedIban.of(payload.paidTo)) { "A paid declaration keeps the account it went to" }
    val markdownContent =
        listOf(
            "Dear ${recipient.fullName},",
            "",
            "The board paid your declaration ${named(payload.title, payload.totalCents)}. The money went to your bank account $account.",
            "",
            "If the money has not arrived in a few days, reply to this email. Your reply goes to the board.",
            "",
            "Kind regards,",
            "The board of ESA Blueshell",
        ).joinToString("\n")
    return EmailContent(
        recipientEmail = recipient.email,
        recipientName = recipient.fullName,
        subject = "Your declaration is paid",
        markdownContent = markdownContent,
        replyToOverride = BOARD,
    )
}

private fun named(
    title: String,
    totalCents: Int,
) = "**$title** of €${String.format(Locale.ROOT, "%.2f", totalCents / CENTS)}"

private const val CENTS = 100.0
private const val BOARD = "board@blueshell.utwente.nl"
