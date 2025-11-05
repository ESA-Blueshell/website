package net.blueshell.api.factory.model;

import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.User;
import net.blueshell.api.model.contribution.Contribution;
import net.blueshell.api.model.contribution.ContributionPeriod;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for Contribution model test instances.
 */
@Component
@RequiredArgsConstructor
public class ContributionFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;
    private final UserFactory userFactory;
    private final ContributionPeriodFactory contributionPeriodFactory;

    public Contribution createBasic() {
        Contribution c = new Contribution();
        c.setId(generateId());
        User user = userFactory.createBasic();
        ContributionPeriod period = contributionPeriodFactory.createBasic();
        c.setUser(user);
        c.setUserId(user.getId());
        c.setContributionPeriod(period);
        c.setContributionPeriodId(period.getId());
        return c;
    }

    public Contribution createFull() {
        return createBasic();
    }

    public Contribution createWithCustomizations(java.util.function.Consumer<Contribution> customizer) {
        Contribution c = createFull();
        customizer.accept(c);
        return c;
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
