package net.blueshell.api.user.api

import net.blueshell.api.user.domain.sealing.LocalSealer
import org.mockito.kotlin.mock
import tools.jackson.databind.json.JsonMapper

/** Sealing as the unit tests use it: the stand-in sealer, so a value opens only for its own member. */
object TestSealing {
    val addresses: SealedAddresses = SealedAddresses(LocalSealer(), mock(), JsonMapper.builder().build(), mock(), "api-address")
}
