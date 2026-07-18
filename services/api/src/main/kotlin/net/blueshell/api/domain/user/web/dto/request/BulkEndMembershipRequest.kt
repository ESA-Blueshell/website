package net.blueshell.api.domain.user.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive

@Schema(name = "BulkEndMembershipRequest")
data class BulkEndMembershipRequest(
    @field:NotEmpty(message = "At least one user ID is required")
    val userIds: List<@Positive Long> = emptyList(),
)
