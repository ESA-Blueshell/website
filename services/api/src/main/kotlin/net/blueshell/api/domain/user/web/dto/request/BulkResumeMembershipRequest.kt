package net.blueshell.api.domain.user.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty

@Schema(name = "BulkResumeMembershipRequest")
data class BulkResumeMembershipRequest(
    @field:NotEmpty
    var userIds: List<Long> = emptyList(),
)
