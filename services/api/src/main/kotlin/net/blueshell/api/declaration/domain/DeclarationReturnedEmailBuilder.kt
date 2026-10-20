package net.blueshell.api.declaration.domain

import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.user.persistence.User
import java.util.Locale

/** The board's reason for sending a declaration back or rejecting it, quoted to its submitter. */
fun declarationReturnedEmail(
    recipient: User,
    payload: DeclarationJobs.DeclarationReturnedPayload,
    frontendUrl: String,
): EmailContent {
    val named = "**${payload.title}** of €${String.format(Locale.ROOT, "%.2f", payload.totalCents / CENTS)}"
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

private const val CENTS = 100.0
private const val BOARD = "board@blueshell.utwente.nl"
