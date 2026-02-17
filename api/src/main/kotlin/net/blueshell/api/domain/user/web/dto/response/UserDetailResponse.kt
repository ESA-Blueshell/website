package net.blueshell.api.domain.user.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import net.blueshell.api.shared.enums.Role
import java.sql.Date
import java.time.Instant

@Schema(name = "UserDetailResponse")
data class UserDetailResponse(
    var id: Long,
    var roles: Set<Role>,
    var enabled: Boolean,
    var username: String,
    var initials: String,
    var firstName: String,
    var prefix: String? = null,
    var lastName: String,
    var fullName: String,
    var newsletter: Boolean,
    var email: String,
    var discord: String,
    var phoneNumber: String,
    var createdAt: Instant,
    var updatedAt: Instant,
    var version: Long
)
