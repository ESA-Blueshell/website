package net.blueshell.api.service;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.model.Contribution;
import net.blueshell.api.model.ContributionPeriod;
import net.blueshell.api.repository.ContributionRepository;
import net.blueshell.api.service.brevo.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class ContributionService extends BaseModelService<Contribution, Long, ContributionRepository> {

    private final ContributionPeriodService periodService;
    private final EmailService emailService;

    @Autowired
    public ContributionService(ContributionRepository repository,  EmailService emailService, ContributionPeriodService periodService) {
        super(repository);
        this.emailService = emailService;
        this.periodService = periodService;
    }

    @Transactional(readOnly = true)
    public List<Contribution> findByContributionPeriodId(Long contributionPeriodId) {
        ContributionPeriod contributionPeriod = periodService.findById(contributionPeriodId);
        return repository.findByContributionPeriod(contributionPeriod);
    }

    @Transactional(readOnly = true)
    public List<Contribution> findByContributionPeriodIdAndPaid(Long periodId, Boolean paid) {
        ContributionPeriod contributionPeriod = periodService.findById(periodId);
        return repository.findByContributionPeriodAndPaid(contributionPeriod, paid);
    }

    @Transactional
    public void sendReminder(List<Contribution> unpaidContributions) {
        emailService.sendContributionReminders(unpaidContributions);
        Timestamp remindedAt = Timestamp.from(Instant.now());
        unpaidContributions.forEach(contribution -> contribution.setRemindedAt(remindedAt));
        self().updateAll(unpaidContributions);
    }
}
