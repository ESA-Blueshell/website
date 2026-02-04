package net.blueshell.api.repository

import net.blueshell.api.base.BaseRepository
import net.blueshell.api.model.Membership
import org.springframework.stereotype.Repository

@Repository
interface MemberRepository : BaseRepository<Membership, Long> {
    fun existsByUserId(userId: Long): Boolean
}
