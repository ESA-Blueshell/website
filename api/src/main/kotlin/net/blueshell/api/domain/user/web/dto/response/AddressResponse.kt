package net.blueshell.api.domain.user.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.dto.AuditedAutoIdDTO

@Schema(name = "AddressResponse")
data class AddressResponse(
    var country: String? = null,
    var city: String? = null,
    var street: String? = null,
    var houseNumber: String? = null,
    var zipCode: String? = null
) : AuditedAutoIdDTO()
