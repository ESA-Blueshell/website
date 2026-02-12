package net.blueshell.api.domain.user.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.dto.AuditedAutoIdDTO

@Schema(name = "UserSummaryResponse")
data class UserSummaryResponse(
    var username: String? = null,
    var initials: String? = null,
    var firstName: String? = null,
    var prefix: String? = null,
    var lastName: String? = null,
    var fullName: String? = null,
    var newsletter: Boolean? = null,
    var addressId: Long? = null,
    var email: String? = null,
    var discord: String? = null,
    var phoneNumber: String? = null
) : AuditedAutoIdDTO()
