package net.blueshell.api.discord.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.discord.domain.RoleOpeningState
import net.blueshell.api.discord.domain.RoleOpenings
import net.blueshell.api.discord.persistence.RoleAccess
import net.blueshell.api.security.BoardOnly
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Schema(name = "RoleOpeningRequest", description = "The access a role gets to a channel or category")
data class RoleOpeningRequest(
    val access: RoleAccess,
)

@Schema(name = "RoleChannelRequest", description = "A new text channel under a category, opened to the role")
data class RoleChannelRequest(
    val name: String,
    val category: String,
    val access: RoleAccess,
)

/** What a Discord role opens, as its page sets it. Each change answers what the role opens now. */
@RestController
@RequestMapping("/management/discord/roles/{roleId}/opens")
@Tag(name = "Discord management", description = "What the site keeps in sync on Discord")
@BoardOnly
class DiscordRoleController(
    private val openings: RoleOpenings,
) {
    @GetMapping
    fun listRoleOpenings(
        @PathVariable roleId: String,
    ): List<RoleOpeningState> = openings.read(roleId)

    /** Opens a channel or category to the role at an access, or changes it; Discord is written now. */
    @PutMapping("/{channelId}")
    fun setRoleOpening(
        @PathVariable roleId: String,
        @PathVariable channelId: String,
        @RequestBody request: RoleOpeningRequest,
    ): List<RoleOpeningState> = openings.set(roleId, channelId, request.access)

    /** Takes the role's overwrite off a channel or category; nothing is deleted on Discord. */
    @DeleteMapping("/{channelId}")
    fun removeRoleOpening(
        @PathVariable roleId: String,
        @PathVariable channelId: String,
    ): List<RoleOpeningState> = openings.remove(roleId, channelId)

    @PostMapping
    fun createRoleChannel(
        @PathVariable roleId: String,
        @RequestBody request: RoleChannelRequest,
    ): List<RoleOpeningState> = openings.create(roleId, request.name, request.category, request.access)

    /** Moves one of the role's channels into the archive category. */
    @PostMapping("/{channelId}/archive")
    fun archiveRoleChannel(
        @PathVariable roleId: String,
        @PathVariable channelId: String,
    ): List<RoleOpeningState> = openings.archive(roleId, channelId)
}
