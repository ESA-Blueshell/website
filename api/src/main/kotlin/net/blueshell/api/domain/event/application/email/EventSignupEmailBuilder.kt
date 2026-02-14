package net.blueshell.api.domain.event.application.email

import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.shared.email.EmailContent

/**
 * Email builder for event signup confirmations.
 *
 * Builds EmailContent DTO that serves as Anti-Corruption Layer (ADR-019)
 * between the event domain and the platform email system.
 */
fun createEventSignupEmail(
    eventSignUp: EventSignUp,
    frontendUrl: String,
    appUrl: String
): EmailContent {
    val event = eventSignUp.event
    val guest = requireNotNull(eventSignUp.guest) { "Event signup email requires a guest signup." }

    val editLink = "$frontendUrl/events/signups/edit/${guest.accessToken}"
    val eventDetailsLink = "$frontendUrl/events#${event.id}"

    val eventDate = if (event.startTime != event.endTime) {
        "${event.startTime} - ${event.endTime}"
    } else {
        event.startTime.toString()
    }

    val eventLocation = if (!event.location.isNullOrBlank()) {
        event.location
    } else {
        "Location details will be provided closer to the event date"
    }

    val markdownContent = """
        Dear ${guest.name},

        Thank you for registering for **${event.title}**!

        Your registration has been successfully confirmed. Here are the event details:

        **Event Information:**
        - **Event:** ${event.title}
        - **Date:** $eventDate
        - **Location:** $eventLocation

        **Important Links:**
        - [View full event details]($eventDetailsLink)
        - [Edit your registration]($editLink)

        **What's Next?**
        - Keep an eye on your email for any event updates
        - Join our [Discord community](https://discord.gg/dFam2yqXu7) to connect with other participants
        - Visit our [website]($appUrl) for more upcoming events

        If you need to make changes to your registration or have any questions, please use the edit link above or contact us through our Discord server.

        We're excited to see you at the event!

        Please do not reply to this email, as this is a generated email. Any responses will be ignored.

        Kind regards,
        Blueshell Events Team
    """.trimIndent()

    return EmailContent(
        recipientEmail = guest.email,
        recipientName = guest.name,
        subject = "Event Registration Confirmed - ${event.title}",
        markdownContent = markdownContent,
        senderName = "Blueshell Events"
    )
}
