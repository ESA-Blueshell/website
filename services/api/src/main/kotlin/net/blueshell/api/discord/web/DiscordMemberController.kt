package net.blueshell.api.discord.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import net.blueshell.api.discord.domain.DiscordMemberDirectory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * The Discord person picker's search. Public, because account creation comes before any login: it
 * says only what any member of the server sees, answers at most ten members to a query of two
 * characters or more, and is rate-limited per address.
 */
@Tag(name = "Discord")
@RestController
@RequestMapping("/discord")
class DiscordMemberController(
    private val members: DiscordMemberDirectory,
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
}
