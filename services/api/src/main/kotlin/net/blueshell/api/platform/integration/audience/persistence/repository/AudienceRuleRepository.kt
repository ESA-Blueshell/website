package net.blueshell.api.platform.integration.audience.persistence.repository

import net.blueshell.api.platform.integration.audience.persistence.AudienceFactKind
import net.blueshell.api.platform.integration.audience.persistence.AudienceRule
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface AudienceRuleRepository : BaseRepository<AudienceRule, Long> {
    fun findAllByEnabledTrue(): List<AudienceRule>

    fun findAllByFactKindAndFactKeyAndEnabledTrue(
        factKind: AudienceFactKind,
        factKey: String,
    ): List<AudienceRule>

    fun findAllByAudienceId(audienceId: Long): List<AudienceRule>
}
