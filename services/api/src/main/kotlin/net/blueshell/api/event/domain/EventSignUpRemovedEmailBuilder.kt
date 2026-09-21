package net.blueshell.api.event.domain

import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.job.EmailJobs

fun createEventSignUpRemovedEmail(
    payload: EmailJobs.EventSignUpRemovedPayload,
    frontendUrl: String,
): EmailContent {
    val markdownContent =
        """
        Dear ${payload.recipientName},

        Your sign-up for **${payload.eventTitle}** is removed by the board.

        If you think this is a mistake, or you still want to attend, reach us on our
        [Discord](https://discord.gg/dFam2yqXu7) and we sort it out. You can sign up again from the
        [events page]($frontendUrl/events) while sign-ups are open.

        Please do not reply to this email, as this is a generated email. Any responses will be ignored.

        Kind regards,
        Blueshell Events Team
        """.trimIndent()

    return EmailContent(
        recipientEmail = payload.recipientEmail,
        recipientName = payload.recipientName,
        subject = "Sign-up removed - ${payload.eventTitle}",
        markdownContent = markdownContent,
        senderNameOverride = "Blueshell Events",
    )
}
