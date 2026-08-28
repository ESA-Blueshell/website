package net.blueshell.api.sync.domain

import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.stereotype.Service
import net.blueshell.api.sync.api.ExternalIdMappingService

/**
 * Pushes one aggregate to every supplied target and persists each target's
 * external id in `external_id_mapping`. Extracted so contact and calendar
 * sync services share one fan-out shape.
 */
@Service
class SyncFanOut(private val mappings: ExternalIdMappingService) {
    fun <A : Any> push(
        aggregateType: String,
        aggregateId: Long,
        data: A?,
        targets: List<SyncTarget<A>>,
        postPush: (TargetSystem, String?) -> Unit = { _, _ -> },
    ) {
        targets.forEach { target ->
            val current = mappings.find(aggregateType, aggregateId, target.system.name)?.externalId
            val newId = target.push(aggregateId, data, current)
            mappings.upsert(aggregateType, aggregateId, target.system.name, newId)
            postPush(target.system, newId)
        }
    }
}
