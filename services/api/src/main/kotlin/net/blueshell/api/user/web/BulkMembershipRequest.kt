package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

/** Ends the active membership of every selected member, effective the server's today. */
@Schema(name = "BulkEndMembershipRequest")
data class BulkEndMembershipRequest(
    @field:NotEmpty(message = "Select at least one member.")
    @field:Size(max = 1000, message = "Select at most 1000 members at a time.")
    val userIds: List<@Positive Long> = emptyList(),
)

/** Starts a membership today for every selected member who has none. */
@Schema(name = "BulkStartMembershipRequest")
data class BulkStartMembershipRequest(
    @field:NotEmpty(message = "Select at least one member.")
    @field:Size(max = 1000, message = "Select at most 1000 members at a time.")
    val userIds: List<@Positive Long> = emptyList(),
)
