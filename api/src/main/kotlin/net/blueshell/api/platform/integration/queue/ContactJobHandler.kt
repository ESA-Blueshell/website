package net.blueshell.api.platform.integration.queue

import net.blueshell.api.platform.integration.contact.adapter.ContactAdapter
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
abstract class ContactJobHandler<T : Any>(
    objectMapper: ObjectMapper,
    payloadType: Class<T>,
    adapters: List<ContactAdapter>,
) : AbstractJsonJobHandler<T>(objectMapper, payloadType) {

    private val bySystem: Map<ContactSystem, ContactAdapter> = adapters.associateBy { it.system }

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
    protected abstract fun handleForSystem(payload: T, adapter: ContactAdapter)

    companion object {
        private val log = LoggerFactory.getLogger(ContactJobHandler::class.java)
    }
}
