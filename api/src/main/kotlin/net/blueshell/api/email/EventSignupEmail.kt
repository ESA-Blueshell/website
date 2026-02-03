package net.blueshell.api.email

import net.blueshell.api.base.BaseEmail
import net.blueshell.api.model.User
import net.blueshell.api.model.event.Event
import net.blueshell.api.model.event.EventSignUp

class EventSignupEmail(private val eventSignUp: EventSignUp, frontendUrl: String?, appUrl: String?) : BaseEmail(
    createRecipientFromSignUp(
        eventSignUp
    ), frontendUrl, appUrl
) {
    override fun getSubject(): String {
        return String.format("Event Registration Confirmed - %s", eventSignUp.event.title)
    }

    override fun getMarkdownContent(): String {
        val event = eventSignUp.event
        val editLink = String.format(frontendUrl + "/events/signups/edit/%s", eventSignUp.guest.accessToken)

        val eventDetailsLink = String.format(frontendUrl + "/events#%d", event.id)

        return String.format(
            """
                        Dear %s,
                        
                        Thank you for registering for **%s**!
                        
                        Your registration has been successfully confirmed. Here are the event details:
                        
                        **Event Information:**
                        - **Event:** %s
                        - **Date:** %s
                        - **Location:** %s
                        
                        **Important Links:**
                        - [View full event details](%s)
                        - [Edit your registration](%s)
                        
                        **What's Next?**
                        - Keep an eye on your email for any event updates
                        - Join our [Discord community](https://discord.gg/dFam2yqXu7) to connect with other participants
                        - Visit our [website](%s) for more upcoming events
                        
                        If you need to make changes to your registration or have any questions, please use the edit link above or contact us through our Discord server.
                        
                        We're excited to see you at the event!
                        
                        Please do not reply to this email, as this is a generated email. Any responses will be ignored.
                        
                        Kind regards,
                        Blueshell Events Team
                        
                        """.trimIndent(),
            eventSignUp.guest.name,
            event.title,
            event.title,
            formatEventDate(event), formatEventLocation(event),
            eventDetailsLink,
            editLink,
            appUrl
        )
    }

    override fun getSenderName(): String {
        return "Blueshell Events"
    }

    private fun formatEventDate(event: Event): String {
        if (event.startTime != null) {
            if (event.endTime != null && event.startTime != event.endTime) {
                return String.format("%s - %s", event.startTime, event.endTime)
            }
            return event.startTime.toString()
        }
        return "TBA"
    }

    private fun formatEventLocation(event: Event): String {
        if (event.location != null && !event.location.trim { it <= ' ' }.isEmpty()) {
            return event.location
        }
        return "Location details will be provided closer to the event date"
    }

    companion object {
        private fun createRecipientFromSignUp(signUp: EventSignUp): User {
            val guestUser = User()
            guestUser.email = signUp.guest.email
            guestUser.firstName = signUp.guest.name
            return guestUser
        }
    }
}
