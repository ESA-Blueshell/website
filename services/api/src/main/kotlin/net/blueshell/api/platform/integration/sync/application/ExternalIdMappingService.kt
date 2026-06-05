package net.blueshell.api.platform.integration.sync.application

import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
import net.blueshell.api.platform.integration.sync.persistence.repository.ExternalIdMappingRepository
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ExternalIdMappingService(
    private val repository: ExternalIdMappingRepository,
) {
    @Transactional(readOnly = true)
    fun find(aggregateType: String, aggregateId: Long, system: String): ExternalIdMapping? =
        repository.findByAggregateTypeAndAggregateIdAndSystem(aggregateType, aggregateId, system)

    @Transactional(readOnly = true)
    fun findBatch(aggregateType: String, aggregateIds: Collection<Long>, system: String): List<ExternalIdMapping> {
        if (aggregateIds.isEmpty()) return emptyList()
        return repository.findByAggregateTypeAndSystemAndAggregateIdIn(aggregateType, system, aggregateIds)
    }

    @Transactional(readOnly = true)
    fun findByExternalIds(aggregateType: String, system: String, externalIds: Collection<String>): List<ExternalIdMapping> {
        if (externalIds.isEmpty()) return emptyList()
        return repository.findByAggregateTypeAndSystemAndExternalIdIn(aggregateType, system, externalIds)
    }

    @Transactional(readOnly = true)
    fun findOwner(aggregateType: String, system: String, externalId: String): ExternalIdMapping? =
        repository.findFirstByAggregateTypeAndSystemAndExternalId(aggregateType, system, externalId)

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

    /**
     * Links [externalUserId] on [system] to [userId]. Idempotent for the
     * same (userId, system, externalUserId) triple; updates the external id
     * if the same user's id changed; throws [ExternalIdConflictException] if
     * the external id is already owned by a different user.
     */
    @Transactional
    fun linkUser(userId: Long, system: TargetSystem, externalUserId: String): ExternalIdMapping {
        require(externalUserId.isNotBlank()) { "externalUserId must not be blank" }
        val owner = findOwner(USER_AGGREGATE, system.name, externalUserId)
        if (owner != null && owner.aggregateId != userId) {
            throw ExternalIdConflictException(owner.aggregateId, system, externalUserId)
        }
        upsert(USER_AGGREGATE, userId, system.name, externalUserId)
        return repository.findByAggregateTypeAndAggregateIdAndSystem(USER_AGGREGATE, userId, system.name)!!
    }

    companion object {
        const val USER_AGGREGATE = "USER"
        const val COHORT_AGGREGATE = "COHORT"
    }
}

