package net.blueshell.api.factory.model;

import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.User;
import net.blueshell.api.model.committee.Committee;
import net.blueshell.api.model.committee.CommitteeMember;
import org.springframework.stereotype.Component;

/**
 * Factory for CommitteeMember model test instances.
 */
@Component
@RequiredArgsConstructor
public class CommitteeMemberFactory {

    private final Faker faker;
    private final UserFactory userFactory;
    private final CommitteeFactory committeeFactory;

    public CommitteeMember createBasic() {
        CommitteeMember member = new CommitteeMember();
        User user = userFactory.createBasic();
        Committee committee = committeeFactory.createBasic();

        member.setUserId(user.getId());
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
            member.setCommitteeId(committee.getId());
        });
    }

    public CommitteeMember createForUser(User user) {
        return createWithCustomizations(member -> {
            member.setUserId(user.getId());
        });
    }

}
