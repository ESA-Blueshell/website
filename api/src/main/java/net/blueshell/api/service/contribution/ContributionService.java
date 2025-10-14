package net.blueshell.api.service.contribution;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.model.contribution.Contribution;
import net.blueshell.api.model.contribution.ContributionPeriod;
import net.blueshell.api.repository.contribution.ContributionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ContributionService extends BaseModelService<Contribution, ContributionRepository> {

    private final ContributionPeriodService periodService;

    @Autowired
    public ContributionService(ContributionRepository repository, ContributionPeriodService periodService) {
        super(repository);
        this.periodService = periodService;
    }

    @Transactional(readOnly = true)
    public List<Contribution> findByContributionPeriodId(Long contributionPeriodId) {
        ContributionPeriod contributionPeriod = periodService.findById(contributionPeriodId);
        return repository.findByContributionPeriod(contributionPeriod);
    }
}
