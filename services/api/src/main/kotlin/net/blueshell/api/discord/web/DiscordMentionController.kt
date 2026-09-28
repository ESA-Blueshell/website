package net.blueshell.api.discord.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import net.blueshell.api.discord.domain.DiscordChannelDirectory
import net.blueshell.api.discord.domain.DiscordMemberDirectory
import net.blueshell.api.discord.domain.DiscordRoleDirectory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * What a description's mentions name. Public, because descriptions are read in public: it says only
 * what any member of the server sees, and never names a channel hidden from @everyone.
 */
@Tag(name = "Discord")
@RestController
@RequestMapping("/discord")
class DiscordMentionController(
    private val members: DiscordMemberDirectory,
    private val roles: DiscordRoleDirectory,
    private val channels: DiscordChannelDirectory,
) {
    @PermitAll
    @GetMapping("/mentions")
    @Operation(
        operationId = "readDiscordMentions",
        summary = "The names of the members, roles and channels a description mentions; an ID the server lacks is left out",
    )
    @ApiResponse(responseCode = "200", content = [Content(schema = Schema(implementation = DiscordMentionsResponse::class))])
    @ApiResponse(responseCode = "503", description = "The bot is not set up, or Discord did not answer", content = [Content()])
    fun mentions(
        @RequestParam(required = false, defaultValue = "") users: List<String>,
        @RequestParam(required = false, defaultValue = "") roles: List<String>,
        @RequestParam(required = false, defaultValue = "") channels: List<String>,
    ): ResponseEntity<DiscordMentionsResponse> {
        val people = if (users.isEmpty()) emptyList() else members.named(asked(users))
        val ranks = if (roles.isEmpty()) emptyList() else this.roles.named(asked(roles))
        val wanted = asked(channels)
        val rooms = if (channels.isEmpty()) emptyList() else this.channels.open()?.filter { it.id in wanted }
        if (people == null || ranks == null || rooms == null) return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        return ResponseEntity.ok(
            DiscordMentionsResponse(
                users = people.map { DiscordNameResponse(it.id, it.name) },
                roles = ranks.map { DiscordRoleNameResponse(it.id, it.name, it.colour) },
                channels = rooms.map { DiscordNameResponse(it.id, it.name) },
            ),
        )
    }

    /* What a description may mention, for whoever writes one, so it needs a login. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/channels")
    @Operation(operationId = "listDiscordChannels", summary = "The Discord server's channels everybody can see, in the server's order")
    @ApiResponse(
        responseCode = "200",
        content = [Content(array = ArraySchema(schema = Schema(implementation = DiscordNameResponse::class)))],
    )
    @ApiResponse(responseCode = "503", description = "The bot is not set up, or Discord did not answer", content = [Content()])
    fun channels(): ResponseEntity<List<DiscordNameResponse>> {
        val found = channels.open() ?: return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        return ResponseEntity.ok(found.map { DiscordNameResponse(it.id, it.name) })
    }

    /* A page names a few; a caller asking for thousands is cut short rather than served. */
    private fun asked(ids: List<String>): Set<String> = ids.filter { it.isNotBlank() }.take(MAX_ASKED).toSet()

    private companion object {
        const val MAX_ASKED = 100
    }
}
