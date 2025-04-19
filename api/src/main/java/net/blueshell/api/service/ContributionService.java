package net.blueshell.api.service;

import net.blueshell.api.model.Contribution;
import net.blueshell.api.model.ContributionPeriod;
import net.blueshell.api.repository.ContributionRepository;
import net.blueshell.api.service.brevo.ContactService;
import net.blueshell.api.service.brevo.EmailService;
import net.blueshell.api.base.BaseModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sendinblue.ApiException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class ContributionService extends BaseModelService<Contribution, Long, ContributionRepository> {

    private final ContributionPeriodService periodService;
    private final EmailService emailService;
    private final ContactService contactService;

    @Autowired
    public ContributionService(ContributionRepository repository, ContactService contactService, EmailService emailService, ContributionPeriodService periodService) {
        super(repository);
        this.contactService = contactService;
        this.emailService = emailService;
        this.periodService = periodService;
    }

    @Transactional(readOnly = true)
    public List<Contribution> findByContributionPeriodId(Long contributionPeriodId) {
        ContributionPeriod contributionPeriod = periodService.findById(contributionPeriodId);
        return repository.findByContributionPeriod(contributionPeriod);
    }

    @Transactional
    public void deleteByContributionPeriod(ContributionPeriod contributionPeriod) {
        repository.deleteByContributionPeriod(contributionPeriod);
    }

    @Transactional
    public void createContribution(Contribution contribution) throws ApiException {
        create(contribution); // May fail so do this before any other steps
        contactService.addToContributionPeriodList(contribution.getContributionPeriod(), contribution.getUser());
    }

    @Transactional
    public void deleteContribution(Contribution contribution) throws ApiException {
        deleteById(contribution.getId()); // May fail so do this before any other steps
        contactService.removeFromContributionPeriodList(contribution.getContributionPeriod(), contribution.getUser());
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
        updateAll(unpaidContributions);
    }
}
