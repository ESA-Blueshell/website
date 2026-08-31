package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

/**
 * The members a bulk membership action applies to.
 *
 * One shape for both actions and for both halves of each: ending and starting are told
 * apart by the path, and neither takes anything the other does not. The effective date is
 * deliberately absent — it is the server's today, not something a caller may choose.
 */
@Schema(name = "BulkMembershipRequest")
data class BulkMembershipRequest(
    @field:NotEmpty(message = "Select at least one member.")
    @field:Size(max = 1000, message = "Select at most 1000 members at a time.")
    val userIds: List<@Positive Long> = emptyList(),
)
