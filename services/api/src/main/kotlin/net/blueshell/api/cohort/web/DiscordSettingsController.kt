package net.blueshell.api.cohort.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.cohort.domain.ServerCohortRole
import net.blueshell.api.cohort.domain.ServerCohortRoles
import net.blueshell.api.security.BoardOnly
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Schema(name = "ServerCohortRoleRequest", description = "The role a server-wide cohort follows")
data class ServerCohortRoleRequest(
    @param:Schema(description = "An existing role to follow")
    val roleId: String? = null,
    @param:Schema(description = "Create a new role named after the cohort, where no role is named")
    val create: Boolean = false,
)

/** The Discord settings page's server-wide cohorts, and the role each one follows. */
@RestController
@RequestMapping("/management/discord/settings/cohorts")
@Tag(name = "Discord settings", description = "The Discord roles and defaults the board sets for the whole server")
@BoardOnly
class DiscordSettingsController(
    private val roles: ServerCohortRoles,
) {
    @GetMapping
    fun listServerCohortRoles(): List<ServerCohortRole> = roles.read()

    @PutMapping("/{key}")
    fun setServerCohortRole(
        @PathVariable key: String,
        @RequestBody request: ServerCohortRoleRequest,
    ): List<ServerCohortRole> = roles.set(key, request.roleId, request.create)
}
