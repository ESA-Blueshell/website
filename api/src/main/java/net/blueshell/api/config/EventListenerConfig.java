package net.blueshell.api.config;

import net.blueshell.api.event.*;
import net.blueshell.api.service.CommitteeMemberService;
import net.blueshell.api.service.UserService;
import net.blueshell.api.service.brevo.ContactService;
import net.blueshell.api.service.brevo.EmailService;
import net.blueshell.api.service.google.CalendarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;

public class EventListenerConfig {

    @Bean
    public CommitteeMemberEventListener committeeMemberEventListener(UserService users) {
        return new CommitteeMemberEventListener(users);
    }

    @Bean
    public ContributionEventListener contributionEventListener(ContactService contacts) {
        return new ContributionEventListener(contacts);
    }

    @Bean
    public ContributionPeriodEventListener contributionPeriodEventListener(ContactService contacts) {
        return new ContributionPeriodEventListener(contacts);
    }

    @Bean
    public EventEventListener eventEventListener(CalendarService calendars) {
        return new EventEventListener(calendars);
    }

    @Bean
    public UserEventListener committeeMemberEventListener(EmailService emails, ContactService contacts, CommitteeMemberService committeeMembers) {
        return new UserEventListener(emails, contacts, committeeMembers);
    }
}
