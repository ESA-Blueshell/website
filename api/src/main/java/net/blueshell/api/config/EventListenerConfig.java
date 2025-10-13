package net.blueshell.api.config;

import net.blueshell.api.event.*;
import net.blueshell.api.job.brevo.RemoveContactFromListJob;
import net.blueshell.api.job.brevo.SyncContactJob;
import net.blueshell.api.service.CommitteeMemberService;
import net.blueshell.api.service.contribution.ContributionPeriodService;
import net.blueshell.api.service.FileService;
import net.blueshell.api.service.UserService;
import net.blueshell.api.service.brevo.ContactService;
import net.blueshell.api.service.email.EmailService;
import net.blueshell.api.service.google.CalendarService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventListenerConfig {

    @Bean
    public CommitteeMemberEventListener committeeMemberEventListener(UserService users) {
        return new CommitteeMemberEventListener(users);
    }

    @Bean
    public ContributionEventListener contributionEventListener(ContactService contacts, RemoveContactFromListJob removeContactFromListJob) {
        return new ContributionEventListener(contacts, removeContactFromListJob);
    }

    @Bean
    public ContributionPeriodEventListener contributionPeriodEventListener(ContactService contacts, ContributionPeriodService periods) {
        return new ContributionPeriodEventListener(contacts, periods);
    }

    @Bean
    public EventEventListener eventEventListener(CalendarService calendars) {
        return new EventEventListener(calendars);
    }

    @Bean
    public UserEventListener userEventListener(EmailService emails, SyncContactJob syncContactJob, CommitteeMemberService committeeMembers) {
        return new UserEventListener(emails, syncContactJob, committeeMembers);
    }

    @Bean
    public FileEventListener fileEventListener(FileService files) {
        return new FileEventListener(files);
    }
}
