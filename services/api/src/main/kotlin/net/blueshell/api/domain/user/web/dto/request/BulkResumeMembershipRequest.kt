package net.blueshell.api.domain.user.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

@Schema(name = "BulkResumeMembershipRequest")
data class BulkResumeMembershipRequest(
    @field:NotEmpty(message = "At least one user ID is required")
    @field:Size(min = 1, max = 1000, message = "userIds must contain between 1 and 1000 entries")
    val userIds: List<@Positive Long> = emptyList(),
)
