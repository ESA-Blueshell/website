package net.blueshell.api.membership.repository

import net.blueshell.api.membership.model.Membership
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface MemberRepository : BaseRepository<Membership, Long> {
    fun existsByUserId(userId: Long): Boolean
}
