package net.blueshell.api.sync.persistence

import net.blueshell.api.shared.repository.BaseRepository

interface ExternalIdMappingRepository : BaseRepository<ExternalIdMapping, Long> {
    fun findByAggregateTypeAndAggregateIdAndSystem(
        aggregateType: String,
        aggregateId: Long,
        system: String,
    ): ExternalIdMapping?

    fun findByAggregateTypeAndSystemAndAggregateIdIn(
        aggregateType: String,
        system: String,
        aggregateIds: Collection<Long>,
    ): List<ExternalIdMapping>

    fun findByAggregateTypeAndSystemAndExternalIdIn(
        aggregateType: String,
        system: String,
        externalIds: Collection<String>,
    ): List<ExternalIdMapping>

    fun findFirstByAggregateTypeAndSystemAndExternalId(
        aggregateType: String,
        system: String,
        externalId: String,
    ): ExternalIdMapping?
}
