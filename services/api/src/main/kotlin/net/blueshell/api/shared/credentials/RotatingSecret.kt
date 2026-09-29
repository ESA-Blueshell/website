package net.blueshell.api.shared.credentials

import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment

/**
 * A credential read each time it is used, so a key rotated in Vault reaches a client without a
 * restart (api ADR-033). A key that goes blank keeps the last value it had: a client that works
 * is not swapped for one that cannot log in.
 */
class RotatingSecret(
    private val environment: Environment,
    private val property: String,
) {
    @Volatile private var last: String = ""

    fun current(): String {
        val now = environment.getProperty(property).orEmpty()
        if (now.isNotBlank()) {
            last = now
        } else if (last.isNotBlank()) {
            log.warn("{} is blank; keeping the value in use", property)
        }
        return last
    }

    private companion object {
        private val log = LoggerFactory.getLogger(RotatingSecret::class.java)
    }
}
