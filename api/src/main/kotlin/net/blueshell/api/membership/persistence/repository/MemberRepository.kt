package net.blueshell.api.membership.persistence.repository

import net.blueshell.api.membership.persistence.Membership
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface MemberRepository : BaseRepository<Membership, Long> {
    fun existsByUserId(userId: Long): Boolean
}