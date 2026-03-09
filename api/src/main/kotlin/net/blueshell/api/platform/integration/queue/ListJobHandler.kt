package net.blueshell.api.platform.integration.queue

import net.blueshell.api.platform.integration.contact.adapter.ListAdapter
import net.blueshell.api.shared.enums.ContactSystem
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper

/**
 * Base class for job handlers that route to a single external [ContactSystemAdapter].
 *
 * Resolves the target adapter by [ContactSystem] from the payload, then delegates to
 * [handleForSystem]. If no adapter is registered for the requested system, logs a warning
 * and returns without throwing.
 */
abstract class ListJobHandler<T : Any>(
    objectMapper: ObjectMapper,
    payloadType: Class<T>,
    adapters: List<ListAdapter>,
) : AbstractJsonJobHandler<T>(objectMapper, payloadType) {

    private val bySystem: Map<ContactSystem, ListAdapter> = adapters.associateBy { it.system }

    final override fun handlePayload(payload: T) {
        val system = systemFrom(payload)
        val adapter = bySystem[system]
        if (adapter == null) {
            log.warn("No adapter registered for system {} — skipping {}", system, jobType)
            return
        }
        handleForSystem(payload, adapter)
    }

    protected abstract fun systemFrom(payload: T): ContactSystem
    protected abstract fun handleForSystem(payload: T, adapter: ListAdapter)

    companion object {
        private val log = LoggerFactory.getLogger(ListJobHandler::class.java)
    }
}
