package net.blueshell.api.domain.user.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import net.blueshell.api.shared.enums.Role
import java.sql.Date

@Schema(name = "UserDetailResponse")
data class UserDetailResponse(
    var roles: Set<Role>? = null,
    var dateOfBirth: Date? = null,
    var nationality: String? = null,
    var photoConsent: Boolean? = null,
    var ehbo: Boolean? = null,
    var bhv: Boolean? = null,
    var enabled: Boolean? = null,
    var gender: String? = null,
    var studentNumber: String? = null,
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
