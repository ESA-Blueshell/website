package net.blueshell.api.platform.integration.sync.application

import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
import net.blueshell.api.platform.integration.sync.persistence.repository.ExternalIdMappingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ExternalIdMappingService(
    private val repository: ExternalIdMappingRepository,
) {
    @Transactional(readOnly = true)
    fun find(aggregateType: String, aggregateId: Long, system: String): ExternalIdMapping? =
        repository.findByAggregateTypeAndAggregateIdAndSystem(aggregateType, aggregateId, system)

    @Transactional
    fun upsert(aggregateType: String, aggregateId: Long, system: String, externalId: String?) {
        val existing = repository.findByAggregateTypeAndAggregateIdAndSystem(aggregateType, aggregateId, system)
        if (existing != null) {
            existing.externalId = externalId
            repository.save(existing)
            return
        }
        repository.save(ExternalIdMapping(aggregateType, aggregateId, system, externalId))
    }
}
