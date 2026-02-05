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

    public CommitteeMember createBasic(
            User user,
            Committee committee
    ) {
        CommitteeMember member = new CommitteeMember();
        member.setUser(user);
        member.setCommittee(committee);
        member.setRole(faker.options().option("Chair", "Secretary", "Treasurer", "Member"));

        return member;
    }

    public CommitteeMember createFull(
            User user,
            Committee committee
    ) {
        return createBasic(user, committee);
    }

    public CommitteeMember createWithCustomizations(
            java.util.function.Consumer<CommitteeMember> customizer,
            User user,
            Committee committee
    ) {
        CommitteeMember member = createFull(user, committee);
        customizer.accept(member);
        return member;
    }
}
