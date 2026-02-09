package net.blueshell.api.membership.persistence

import net.blueshell.api.membership.domain.model.Membership
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface MemberRepository : BaseRepository<Membership, Long> {
    fun existsByUserId(userId: Long): Boolean
}
