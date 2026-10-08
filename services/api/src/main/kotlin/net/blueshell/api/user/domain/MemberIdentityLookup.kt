package net.blueshell.api.user.domain

import net.blueshell.api.shared.user.MemberIdentities
import net.blueshell.api.shared.user.MemberIdentity
import net.blueshell.api.user.persistence.UserRepository
import org.springframework.stereotype.Component

/** The public identity of accounts, read as a projection so the eager member profile is never loaded. */
@Component
class MemberIdentityLookup(
    private val users: UserRepository,
) : MemberIdentities {
    override fun of(ids: Collection<Long>): Map<Long, MemberIdentity> {
        if (ids.isEmpty()) return emptyMap()
        return users
            .findMemberIdentities(ids)
            .associate { it.id to MemberIdentity(username = it.username, discordId = it.discordId) }
    }
}
