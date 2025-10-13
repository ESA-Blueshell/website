package net.blueshell.api.repository.contribution;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.contribution.Contribution;
import net.blueshell.api.model.contribution.ContributionPeriod;
import net.blueshell.api.model.contribution.ContributionReminder;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContributionReminderRepository extends BaseRepository<ContributionReminder> {
    List<ContributionReminder> findByContributionPeriod(ContributionPeriod contributionPeriod);
}
