package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(name = "AddressResponse")
data class AddressResponse(
    var country: String? = null,
    var city: String? = null,
    var street: String? = null,
    var houseNumber: String? = null,
    var zipCode: String? = null,
    var version: Long,
    var id: Long,
    var userId: Long? = null,
    var createdAt: Instant,
    var updatedAt: Instant,
    @param:Schema(description = "False where the address could not be opened now, or is one of many in a list: its fields are then empty")
    var opened: Boolean,
)
