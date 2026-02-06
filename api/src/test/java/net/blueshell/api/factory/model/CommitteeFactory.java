package net.blueshell.api.factory.model;

import net.blueshell.api.model.committee.CommitteeMember;
import net.blueshell.api.testutil.ModelTestUtils;
import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.committee.Committee;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for Committee model test instances.
 */
@Component
@RequiredArgsConstructor
public class CommitteeFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;

    public Committee createBasic() {
        Committee committee = new Committee();
        committee.setName(faker.company().name() + " Committee");
        committee.setDescription(faker.lorem().paragraph(3));
        committee.setMembers(new ArrayList<>());
        return committee;
    }

    public Committee createFull() {
        return createBasic();
    }

    public Committee createWithCustomizations(java.util.function.Consumer<Committee> customizer) {
        Committee committee = createFull();
        customizer.accept(committee);
        return committee;
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
