package net.blueshell.api.service;

import net.blueshell.api.model.ContributionPeriod;
import net.blueshell.api.repository.ContributionPeriodRepository;
import net.blueshell.api.service.brevo.ContactService;
import net.blueshell.api.base.BaseModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sendinblue.ApiException;

@Service
public class ContributionPeriodService extends BaseModelService<ContributionPeriod, Long, ContributionPeriodRepository> {
    @Autowired
    public ContributionPeriodService(ContributionPeriodRepository repository, ApplicationEventPublisher events) {
        super(repository, events);
    }
}
