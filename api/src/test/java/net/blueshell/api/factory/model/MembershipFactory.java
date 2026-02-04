package net.blueshell.api.factory.model;

import net.blueshell.api.testutil.ModelTestUtils;
import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.common.enums.MemberType;
import net.blueshell.api.model.Membership;
import net.blueshell.api.model.User;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for Membership model test instances.
 */
@Component
@RequiredArgsConstructor
public class MembershipFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;
    private final UserFactory userFactory;

    public Membership createBasic() {
        Membership membership = new Membership();
        ModelTestUtils.setId(membership, generateId());

        User user = userFactory.createBasic();
        membership.setUserId(user.getId());

        membership.setStartDate(LocalDate.now().minusMonths(6));
        membership.setMemberType(faker.options().option(MemberType.class));
        membership.setIncasso(faker.bool().bool());

        return membership;
    }

    public Membership createFull() {
        Membership membership = createBasic();
        if (faker.bool().bool()) {
            membership.setEndDate(LocalDate.now().plusMonths(6));
        }
        return membership;
    }

    public Membership createWithCustomizations(java.util.function.Consumer<Membership> customizer) {
        Membership membership = createFull();
        customizer.accept(membership);
        return membership;
    }

    public Membership createActive() {
        return createWithCustomizations(membership -> {
            membership.setStartDate(LocalDate.now().minusMonths(3));
            membership.setEndDate(null);
        });
    }

    public Membership createExpired() {
        return createWithCustomizations(membership -> {
            membership.setStartDate(LocalDate.now().minusYears(2));
            membership.setEndDate(LocalDate.now().minusYears(1));
        });
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
