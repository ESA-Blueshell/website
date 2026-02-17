package net.blueshell.api.domain.user.persistence.repository

import net.blueshell.api.domain.user.persistence.MemberProfile
import net.blueshell.api.shared.repository.BaseRepository
import java.util.Optional

interface MemberProfileRepository : BaseRepository<MemberProfile, Long> {
    fun findByUser_Id(userId: Long): Optional<MemberProfile>
}
