package net.blueshell.api.event.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import net.blueshell.api.event.domain.PingedRoleData
import net.blueshell.api.event.persistence.PingedRole
import net.blueshell.api.shared.util.SNOWFLAKE

@Schema(description = "A Discord role the bot notifies when it posts the event")
data class PingedRoleRequest(
    @field:Pattern(regexp = SNOWFLAKE, message = "A Discord role ID is a number")
    val id: String,
    @field:NotBlank
    @field:Size(max = 100)
    @field:Schema(description = "The role's name as last known, kept for when Discord cannot be asked")
    val name: String,
)

@Schema(description = "A Discord role the bot notifies when it posts the event")
data class PingedRoleResponse(
    val id: String,
    @field:Schema(description = "The role's name as last known")
    val name: String,
)

fun PingedRoleRequest.asData() = PingedRoleData(id = id, name = name)

fun PingedRole.asResponse() = PingedRoleResponse(id = roleId, name = roleName)
