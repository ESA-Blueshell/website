package net.blueshell.api.factory.model.committee

import com.github.javafaker.Faker
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.committee.persistence.CommitteeMember
import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.domain.user.persistence.User
import org.springframework.stereotype.Component
import java.util.function.Consumer

/**
 * Factory for CommitteeMember model test instances.
 */
@Component
class CommitteeMemberFactory(
    private val faker: Faker,
    private val userFactory: UserFactory,
    private val committeeFactory: CommitteeFactory
) {

    fun createBasic(user: User, committee: Committee): CommitteeMember {
        val member = CommitteeMember()
        member.user = user
        member.committee = committee
        member.role = faker.options().option("Chair", "Secretary", "Treasurer", "Member")
        return member
    }

    fun createFull(user: User, committee: Committee): CommitteeMember = createBasic(user, committee)

    fun createWithCustomizations(
        customizer: Consumer<CommitteeMember>,
        user: User,
        committee: Committee
    ): CommitteeMember {
        val member = createFull(user, committee)
        customizer.accept(member)
        return member
    }
}
