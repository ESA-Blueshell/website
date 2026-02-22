package net.blueshell.api.domain.user.persistence.repository

import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
@Suppress("FunctionName")
interface MemberRepository : BaseRepository<Membership, Long> {
    fun existsByUser_Id(userId: Long): Boolean
    fun existsByUser_IdAndEndDateIsNull(userId: Long): Boolean
}
