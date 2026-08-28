package net.blueshell.api.committee.domain

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * A committee membership as the service accepts it. Lived in the command package
 * while a command carried it; it is an application-layer input either way.
 */
data class CommitteeMemberData(
    @field:NotNull(message = "User ID is required")
    var userId: Long,
    // Absent for a member who simply sits on the committee. Size tolerates null.
    @field:Size(max = 50, message = "Role must be at most 50 characters")
    var role: String?,
)
