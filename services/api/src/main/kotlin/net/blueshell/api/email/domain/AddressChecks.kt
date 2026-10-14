package net.blueshell.api.email.domain

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Checks every sending address each night, so the Addresses page says whether each still sends and is read. */
@Component
class AddressChecks(
    private val addresses: SendingAddresses,
) {
    @Scheduled(cron = "\${email.addresses.check-cron:0 30 5 * * *}")
    fun checkAll() {
        addresses.list().forEach { address ->
            // One address that cannot be checked leaves the others to be.
            runCatching { addresses.check(address.id) }.onFailure { log.warn("Checking address id={} failed: {}", address.id, it.message) }
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(AddressChecks::class.java)
    }
}
