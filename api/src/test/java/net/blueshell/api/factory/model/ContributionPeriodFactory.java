package net.blueshell.api.factory.model;

import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.contribution.ContributionPeriod;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for ContributionPeriod model test instances.
 */
@Component
@RequiredArgsConstructor
public class ContributionPeriodFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;

    public ContributionPeriod createBasic() {
        ContributionPeriod cp = new ContributionPeriod();
        cp.setId(generateId());
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        cp.setStartDate(start);
        cp.setEndDate(start.plusMonths(6));
        cp.setHalfYearFee(10.0);
        cp.setFullYearFee(18.0);
        cp.setAlumniFee(5.0);
        cp.setListId(faker.number().randomNumber());
        return cp;
    }

    public ContributionPeriod createFull() {
        return createBasic();
    }

    public ContributionPeriod createWithCustomizations(java.util.function.Consumer<ContributionPeriod> customizer) {
        ContributionPeriod cp = createFull();
        customizer.accept(cp);
        return cp;
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
