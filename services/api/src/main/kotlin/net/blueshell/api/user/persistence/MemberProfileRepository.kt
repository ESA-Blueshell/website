package net.blueshell.api.user.persistence

import net.blueshell.api.shared.repository.BaseRepository
import java.util.Optional

@Suppress("FunctionName")
interface MemberProfileRepository : BaseRepository<MemberProfile, Long> {
    fun findByUser_Id(userId: Long): Optional<MemberProfile>
}
