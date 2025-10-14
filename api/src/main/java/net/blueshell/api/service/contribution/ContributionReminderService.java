package net.blueshell.api.service.contribution;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.job.email.ContributionReminderEmailJob;
import net.blueshell.api.model.contribution.Contribution;
import net.blueshell.api.model.contribution.ContributionPeriod;
import net.blueshell.api.model.contribution.ContributionReminder;
import net.blueshell.api.repository.contribution.ContributionReminderRepository;
import net.blueshell.api.repository.contribution.ContributionRepository;
import net.blueshell.api.service.email.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

@Service
public class ContributionReminderService extends BaseModelService<ContributionReminder, ContributionReminderRepository> {

    private final ContributionPeriodService periodService;
    private final EmailService emails;
    private final ContributionReminderEmailJob reminderEmailJob;

    @Autowired
    public ContributionReminderService(ContributionReminderRepository repository, EmailService emails, ContributionPeriodService periodService, ContributionReminderEmailJob reminderEmailJob) {
        super(repository);
        this.emails = emails;
        this.periodService = periodService;
        this.reminderEmailJob = reminderEmailJob;
    }

    @Transactional(readOnly = true)
    public List<ContributionReminder> findByContributionPeriodId(Long contributionPeriodId) {
        var contributionPeriod = periodService.findById(contributionPeriodId);
        return repository.findByContributionPeriod(contributionPeriod);
    }

    public void sendReminder(ContributionReminder reminder) {
        reminderEmailJob.send(reminder.getId());
    }

    public void sendReminders(List<ContributionReminder> reminders) {
        for (var reminder : reminders) {
            sendReminder(reminder);
        }
    }
}
