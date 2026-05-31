package net.blueshell.api.platform.integration.audience.persistence.repository

import net.blueshell.api.platform.integration.audience.persistence.AudienceMember
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface AudienceMemberRepository : BaseRepository<AudienceMember, Long> {
    fun findAllByUserId(userId: Long): List<AudienceMember>

    fun findAllByAudienceId(audienceId: Long): List<AudienceMember>

    fun findByAudienceIdAndUserId(audienceId: Long, userId: Long): AudienceMember?
}
