package net.blueshell.api.factory.committee.persistence

import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.committee.persistence.CommitteeMember
import net.blueshell.api.factory.support.FactoryPersistenceSupport
import net.blueshell.api.user.persistence.User
import org.springframework.stereotype.Component

@Component
class CommitteeFactory(
    private val persistence: FactoryPersistenceSupport,
) {
    fun build(
        name: String = "Committee ${System.currentTimeMillis()}",
        description: String = "Committee description",
    ): Committee = Committee(name = name, description = description)

    fun create(
        name: String = "Committee ${System.currentTimeMillis()}",
        description: String = "Committee description",
    ): Committee = persistence.persist(build(name, description))

    fun buildMember(
        committee: Committee,
        user: User,
        role: String = "Member",
    ): CommitteeMember =
        CommitteeMember(
            committee = committee,
            user = user,
            role = role,
        )

    fun createMember(
        committee: Committee,
        user: User,
        role: String = "Member",
    ): CommitteeMember = persistence.persist(buildMember(committee, user, role))
}
