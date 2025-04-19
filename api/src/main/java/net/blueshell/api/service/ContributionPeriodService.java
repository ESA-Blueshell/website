package net.blueshell.api.service;

import net.blueshell.api.model.ContributionPeriod;
import net.blueshell.api.repository.ContributionPeriodRepository;
import net.blueshell.api.service.brevo.ContactService;
import net.blueshell.api.base.BaseModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sendinblue.ApiException;

@Service
public class ContributionPeriodService extends BaseModelService<ContributionPeriod, Long, ContributionPeriodRepository> {

    private final ContactService contactService;

    @Autowired
    public ContributionPeriodService(ContributionPeriodRepository repository, ContactService contactService) {
        super(repository);
        this.contactService = contactService;
    }

    @Transactional
    public void createContributionPeriod(ContributionPeriod contributionPeriod) throws ApiException {
        contactService.createContributionPeriodList(contributionPeriod);
        create(contributionPeriod);
    }

    @Transactional
    public void deleteContributionPeriod(ContributionPeriod contributionPeriod) {
        deleteById(contributionPeriod.getId());
    }
}
