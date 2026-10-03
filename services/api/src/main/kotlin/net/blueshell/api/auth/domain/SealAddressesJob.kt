package net.blueshell.api.auth.domain

import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.user.api.SealedAddresses
import net.blueshell.api.user.api.UserJobs
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Seals every address left in plaintext. Here rather than in `user`, which seals: `jobs` depends on
 * `user`, so a handler there would close a cycle, as for [RoleChangeEmailJob].
 */
@Component
class SealAddressesJob(
    objectMapper: ObjectMapper,
    private val addresses: SealedAddresses,
) : AbstractJsonJobHandler<UserJobs.SealAddressesPayload>(objectMapper, UserJobs.SealAddresses) {
    override fun handlePayload(payload: UserJobs.SealAddressesPayload) {
        val sealed = addresses.sealEvery()
        if (sealed == 0) skip("Every address is sealed already")
    }
}
