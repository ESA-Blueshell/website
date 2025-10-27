package net.blueshell.api.service.contribution;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.common.event.job.ContributionReminderEmailEvent;
import net.blueshell.api.model.contribution.ContributionReminder;
import net.blueshell.api.repository.contribution.ContributionReminderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ContributionReminderService extends BaseModelService<ContributionReminder, ContributionReminderRepository> {

    private final ContributionPeriodService periodService;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public ContributionReminderService(ContributionReminderRepository repository, ContributionPeriodService periodService, ApplicationEventPublisher eventPublisher) {
        super(repository);
        this.periodService = periodService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<ContributionReminder> findByContributionPeriodId(Long contributionPeriodId) {
        var contributionPeriod = periodService.findById(contributionPeriodId);
        return repository.findByContributionPeriod(contributionPeriod);
    }

    public void sendReminder(ContributionReminder reminder) {
        eventPublisher.publishEvent(new ContributionReminderEmailEvent(reminder.getId()));
    }

    public void sendReminders(List<ContributionReminder> reminders) {
        for (var reminder : reminders) {
            sendReminder(reminder);
        }
    }
}
