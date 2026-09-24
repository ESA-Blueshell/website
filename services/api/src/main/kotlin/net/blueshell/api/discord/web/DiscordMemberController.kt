package net.blueshell.api.discord.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import net.blueshell.api.discord.domain.DiscordMemberDirectory
import net.blueshell.api.discord.domain.DiscordRoleDirectory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * The Discord person picker's reads. Public, because account creation comes before any login: they
 * say only what any member of the server sees, and are rate-limited per address. A search answers
 * at most ten members to a query of two characters or more; the unclaimed list is what the picker
 * shows before anything is typed.
 */
@Tag(name = "Discord")
@RestController
@RequestMapping("/discord")
class DiscordMemberController(
    private val members: DiscordMemberDirectory,
    private val roles: DiscordRoleDirectory,
) {
    @PermitAll
    @GetMapping("/members")
    @Operation(
        operationId = "searchDiscordMembers",
        summary = "Members of the Discord server whose username or server name starts with the query",
    )
    @ApiResponse(
        responseCode = "200",
        content = [Content(array = ArraySchema(schema = Schema(implementation = DiscordMemberResponse::class)))],
    )
    @ApiResponse(responseCode = "503", description = "The bot is not set up, or Discord did not answer", content = [Content()])
    fun search(
        @RequestParam query: String,
    ): ResponseEntity<List<DiscordMemberResponse>> {
        val found = members.search(query) ?: return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        return ResponseEntity.ok(found.map { it.toResponse() })
    }

    @PermitAll
    @GetMapping("/members/unclaimed")
    @Operation(
        operationId = "listUnclaimedDiscordMembers",
        summary = "Members of the Discord server no website account has linked yet, by name",
    )
    @ApiResponse(
        responseCode = "200",
        content = [Content(array = ArraySchema(schema = Schema(implementation = DiscordMemberResponse::class)))],
    )
    @ApiResponse(responseCode = "503", description = "The bot is not set up, or Discord did not answer", content = [Content()])
    fun unclaimed(): ResponseEntity<List<DiscordMemberResponse>> {
        val found = members.unclaimed() ?: return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        return ResponseEntity.ok(found.map { it.toResponse() })
    }

    /* What an event may ping: the server's roles, which any member of it sees anyway. */
    @PermitAll
    @GetMapping("/roles")
    @Operation(operationId = "listDiscordRoles", summary = "The Discord server's roles an event may ping, in the server's order")
    @ApiResponse(
        responseCode = "200",
        content = [Content(array = ArraySchema(schema = Schema(implementation = DiscordRoleResponse::class)))],
    )
    @ApiResponse(responseCode = "503", description = "The bot is not set up, or Discord did not answer", content = [Content()])
    fun roles(): ResponseEntity<List<DiscordRoleResponse>> {
        val found = roles.pingable() ?: return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        return ResponseEntity.ok(found.map { DiscordRoleResponse(it.id, it.name) })
    }
}
