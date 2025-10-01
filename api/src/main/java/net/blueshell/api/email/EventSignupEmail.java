package net.blueshell.api.email;

import net.blueshell.api.base.BaseEmail;
import net.blueshell.api.model.event.Event;
import net.blueshell.api.model.event.EventSignUp;
import net.blueshell.api.model.User;

public class EventSignupEmail extends BaseEmail {

    private final EventSignUp eventSignUp;

    public EventSignupEmail(EventSignUp eventSignUp, String frontendUrl, String appUrl) {
        super(createRecipientFromSignUp(eventSignUp), frontendUrl, appUrl);
        this.eventSignUp = eventSignUp;
    }

    private static User createRecipientFromSignUp(EventSignUp signUp) {
        User guestUser = new User();
        guestUser.setEmail(signUp.getGuest().getEmail());
        guestUser.setFirstName(signUp.getGuest().getName());
        return guestUser;
    }

    @Override
    public String getSubject() {
        return String.format("Event Registration Confirmed - %s", eventSignUp.getEvent().getTitle());
    }

    @Override
    public String getMarkdownContent() {
        Event event = eventSignUp.getEvent();
        String editLink = String.format(frontendUrl + "/events/signups/edit/%s", eventSignUp.getGuest().getAccessToken());

        String eventDetailsLink = String.format(frontendUrl + "/events#%d", event.getId());

        return String.format("""
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
                        """,
                eventSignUp.getGuest().getName(),
                event.getTitle(),
                event.getTitle(),
                formatEventDate(event), formatEventLocation(event),
                eventDetailsLink,
                editLink,
                appUrl
        );
    }

    @Override
    public String getSenderName() {
        return "Blueshell Events";
    }

    @Override
    public String getSenderAddress() {
        return "events@blueshell.utwente.nl";
    }

    private String formatEventDate(Event event) {
        if (event.getStartTime() != null) {
            if (event.getEndTime() != null && !event.getStartTime().equals(event.getEndTime())) {
                return String.format("%s - %s", event.getStartTime(), event.getEndTime());
            }
            return event.getStartTime().toString();
        }
        return "TBA";
    }

    private String formatEventLocation(Event event) {
        if (event.getLocation() != null && !event.getLocation().trim().isEmpty()) {
            return event.getLocation();
        }
        return "Location details will be provided closer to the event date";
    }
}