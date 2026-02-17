package net.blueshell.api.domain.user.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import net.blueshell.api.domain.user.web.validation.ValidMobilePhoneNumber
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import java.time.Instant

@Schema(name = "PersonalInfo")
open class PersonalInfoResponse(
    var email: String,
    var discord: String,
    var phoneNumber: String,
    var version: Long,
    var userId: Long,
    var createdAt: Instant,
    var updatedAt: Instant
)