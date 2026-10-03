package net.blueshell.api.event.domain

import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.shared.email.EmailContent
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val AMSTERDAM: ZoneId = ZoneId.of("Europe/Amsterdam")
private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(AMSTERDAM)
private val HOURS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH).withZone(AMSTERDAM)

/**
 * The email for a guest the board added. It promises nothing about the event itself: the board
 * also adds people after it has ended.
 */
fun createEventSignUpAddedEmail(
    eventSignUp: EventSignUp,
    frontendUrl: String,
    guestAccessToken: String,
): EmailContent {
    val event = eventSignUp.event
    val guest = requireNotNull(eventSignUp.guest) { "The added email is for a guest sign-up." }

    val whenSaid = "${DAY.format(event.startTime)}, ${HOURS.format(event.startTime)} to ${HOURS.format(event.endTime)}"
    val details =
        listOfNotNull(
            "- **When:** $whenSaid (Amsterdam time)",
            event.location?.takeIf { it.isNotBlank() }?.let { "- **Where:** $it" },
        ).joinToString("\n")

    val markdownContent =
        """
        |Dear ${guest.name},
        |
        |The board added you to the sign-ups of **${event.title}**.
        |
        |$details
        |
        |- [View the event]($frontendUrl/events/${event.id})
        |- [View or change your sign-up]($frontendUrl/events/signups/edit#accessToken=$guestAccessToken)
        |
        |If you think this is a mistake, reach us on our [Discord](https://discord.gg/dFam2yqXu7) and we sort it out.
        |
        |Please do not reply to this email, as this is a generated email. Any responses will be ignored.
        |
        |Kind regards,
        |Blueshell Events Team
        """.trimMargin()

    return EmailContent(
        recipientEmail = guest.email,
        recipientName = guest.name,
        subject = "Added to the sign-ups - ${event.title}",
        markdownContent = markdownContent,
        senderNameOverride = "Blueshell Events",
    )
}
