package net.blueshell.api.sync.api

import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.sync.persistence.ExternalIdMapping
import net.blueshell.api.sync.persistence.ExternalIdMappingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import org.springframework.transaction.annotation.Propagation

@Service
class ExternalIdMappingService(
    private val repository: ExternalIdMappingRepository,
) {
    @Transactional(readOnly = true)
    fun find(
        aggregateType: String,
        aggregateId: Long,
        system: String,
    ): ExternalIdMapping? = repository.findByAggregateTypeAndAggregateIdAndSystem(aggregateType, aggregateId, system)

    @Transactional(readOnly = true)
    fun findBatch(
        aggregateType: String,
        aggregateIds: Collection<Long>,
        system: String,
    ): List<ExternalIdMapping> {
        if (aggregateIds.isEmpty()) return emptyList()
        return repository.findByAggregateTypeAndSystemAndAggregateIdIn(aggregateType, system, aggregateIds)
    }

    @Transactional(readOnly = true)
    fun findByExternalIds(
        aggregateType: String,
        system: String,
        externalIds: Collection<String>,
    ): List<ExternalIdMapping> {
        if (externalIds.isEmpty()) return emptyList()
        return repository.findByAggregateTypeAndSystemAndExternalIdIn(aggregateType, system, externalIds)
    }

    @Transactional(readOnly = true)
    fun findOwner(
        aggregateType: String,
        system: String,
        externalId: String,
    ): ExternalIdMapping? = repository.findFirstByAggregateTypeAndSystemAndExternalId(aggregateType, system, externalId)

    @Transactional
    fun upsert(
        aggregateType: String,
        aggregateId: Long,
        system: String,
        externalId: String?,
    ) {
        val existing = repository.findByAggregateTypeAndAggregateIdAndSystem(aggregateType, aggregateId, system)
        if (existing != null) {
            existing.externalId = externalId
            repository.save(existing)
            return
        }
        repository.save(ExternalIdMapping(aggregateType, aggregateId, system, externalId))
    }

    /**
     * Reserves the right to create the external thing for this aggregate and system, so two
     * callers racing (two api pods, a retry beside a fresh run) never create it twice. True for
     * the one caller that may go ahead; a claim left empty since [staleBefore] may be taken over.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun claim(
        aggregateType: String,
        aggregateId: Long,
        system: String,
        staleBefore: Instant,
    ): Boolean =
        repository.insertClaim(aggregateType, aggregateId, system) == 1 ||
            repository.takeOverStaleClaim(aggregateType, aggregateId, system, staleBefore) == 1

    /**
     * Records what was created for a claim, with [fingerprint] saying which version of it is out
     * there. In a transaction of its own, like the claim: once the thing exists outside, a later
     * failure in the caller's work must not roll back the only note that it does.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(
        aggregateType: String,
        aggregateId: Long,
        system: String,
        externalId: String,
        fingerprint: Long,
    ) {
        val mapping =
            repository.findByAggregateTypeAndAggregateIdAndSystem(aggregateType, aggregateId, system)
                ?: ExternalIdMapping(aggregateType, aggregateId, system)
        mapping.externalId = externalId
        mapping.syncedVersion = fingerprint
        repository.save(mapping)
    }

    /** Forgets the external thing, or gives up a claim whose creation failed; committed at once, as [record] is. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun release(
        aggregateType: String,
        aggregateId: Long,
        system: String,
    ) {
        repository.deleteMapping(aggregateType, aggregateId, system)
    }

    /**
     * Links [externalUserId] on [system] to [userId]. Idempotent for the
     * same (userId, system, externalUserId) triple; updates the external id
     * if the same user's id changed; throws [ExternalIdConflictException] if
     * the external id is already owned by a different user.
     */
    @Transactional
    fun linkUser(
        userId: Long,
        system: TargetSystem,
        externalUserId: String,
    ): ExternalIdMapping {
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
