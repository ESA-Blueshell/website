package net.blueshell.api.platform.integration.sync.persistence.repository

import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
import net.blueshell.api.shared.repository.BaseRepository

interface ExternalIdMappingRepository : BaseRepository<ExternalIdMapping, Long> {
    fun findByAggregateTypeAndAggregateIdAndSystem(
        aggregateType: String,
        aggregateId: Long,
        system: String,
    ): ExternalIdMapping?
}
