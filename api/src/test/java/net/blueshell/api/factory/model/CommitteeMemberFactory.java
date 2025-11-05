package net.blueshell.api.factory.model;

import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.User;
import net.blueshell.api.model.committee.Committee;
import net.blueshell.api.model.committee.CommitteeMember;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for CommitteeMember model test instances.
 */
@Component
@RequiredArgsConstructor
public class CommitteeMemberFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;
    private final UserFactory userFactory;
    private final CommitteeFactory committeeFactory;

    public CommitteeMember createBasic() {
        CommitteeMember member = new CommitteeMember();
        member.setId(generateId());

        User user = userFactory.createBasic();
        Committee committee = committeeFactory.createBasic();

        member.setUser(user);
        member.setUserId(user.getId());
        member.setCommittee(committee);
        member.setCommitteeId(committee.getId());
        member.setRole(faker.options().option("Chair", "Secretary", "Treasurer", "Member"));

        return member;
    }

    public CommitteeMember createFull() {
        return createBasic();
    }

    public CommitteeMember createWithCustomizations(java.util.function.Consumer<CommitteeMember> customizer) {
        CommitteeMember member = createFull();
        customizer.accept(member);
        return member;
    }

    public CommitteeMember createForCommittee(Committee committee) {
        return createWithCustomizations(member -> {
            member.setCommittee(committee);
            member.setCommitteeId(committee.getId());
        });
    }

    public CommitteeMember createForUser(User user) {
        return createWithCustomizations(member -> {
            member.setUser(user);
            member.setUserId(user.getId());
        });
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
