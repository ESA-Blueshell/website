package net.blueshell.api.factory.model;

import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.User;
import net.blueshell.api.model.contribution.ContributionPeriod;
import net.blueshell.api.model.contribution.ContributionReminder;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for ContributionReminder model test instances.
 */
@Component
@RequiredArgsConstructor
public class ContributionReminderFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;
    private final UserFactory userFactory;
    private final ContributionPeriodFactory contributionPeriodFactory;

    public ContributionReminder createBasic() {
        ContributionReminder cr = new ContributionReminder();
        cr.setId(generateId());
        User user = userFactory.createBasic();
        ContributionPeriod period = contributionPeriodFactory.createBasic();
        cr.setUser(user);
        cr.setUserId(user.getId());
        cr.setContributionPeriod(period);
        cr.setContributionPeriodId(period.getId());
        return cr;
    }

    public ContributionReminder createFull() {
        return createBasic();
    }

    public ContributionReminder createWithCustomizations(java.util.function.Consumer<ContributionReminder> customizer) {
        ContributionReminder cr = createFull();
        customizer.accept(cr);
        return cr;
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
