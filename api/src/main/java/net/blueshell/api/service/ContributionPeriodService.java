package net.blueshell.api.service;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.model.contribution.ContributionPeriod;
import net.blueshell.api.repository.ContributionPeriodRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContributionPeriodService extends BaseModelService<ContributionPeriod, ContributionPeriodRepository> {
    @Autowired
    public ContributionPeriodService(ContributionPeriodRepository repository, ApplicationEventPublisher events) {
        super(repository);
    }

    @Transactional(readOnly = true)
    public ContributionPeriod findLatest() {
        return repository.findCurrentOrLatestContributionPeriod();
    }
}
