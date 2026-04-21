package net.blueshell.api.factory.committee.persistence

import net.blueshell.api.factory.support.FactoryPersistenceSupport
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.domain.user.persistence.User
import org.springframework.stereotype.Component

@Component
class CommitteeFactory(
    private val persistence: FactoryPersistenceSupport
) {
    fun build(
        name: String = "Committee ${System.currentTimeMillis()}",
        description: String = "Committee description"
    ): Committee {
        return Committee(name = name, description = description)
    }

    fun create(
        name: String = "Committee ${System.currentTimeMillis()}",
        description: String = "Committee description"
    ): Committee {
        return persistence.persist(build(name, description))
    }

    fun buildMember(
        committee: Committee,
        user: User,
        role: String = "Member"
    ): CommitteeMember {
        return CommitteeMember(
            committee = committee,
            user = user,
            role = role,
        )
    }

    fun createMember(
        committee: Committee,
        user: User,
        role: String = "Member"
    ): CommitteeMember {
        return persistence.persist(buildMember(committee, user, role))
    }
}
