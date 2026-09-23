package net.blueshell.api.discord.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import net.blueshell.api.discord.domain.DiscordDoor
import net.blueshell.api.discord.domain.DiscordDoorService
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Duration

/**
 * The site's links into Discord, as redirects: a page links to `/discord/invite/board` and never
 * carries an invite code or channel ID that could expire or go stale. Public, like the links.
 */
@Tag(name = "Discord")
@RestController
@RequestMapping("/discord")
class DiscordDoorController(
    private val doors: DiscordDoorService,
) {
    @PermitAll
    @GetMapping("/invite/{door}")
    @Operation(operationId = "openDiscordInvite", summary = "Redirects to an invite into the door's channel")
    @ApiResponse(responseCode = "302", description = "To the invite", content = [Content()])
    @ApiResponse(responseCode = "404", description = "No such door", content = [Content()])
    fun invite(
        @Parameter(schema = Schema(allowableValues = ["welcome", "board", "suggestions"]))
        @PathVariable door: String,
    ): ResponseEntity<Void> = redirect(DiscordDoor.of(door)?.let(doors::invite))

    @PermitAll
    @GetMapping("/channel/{door}")
    @Operation(operationId = "openDiscordChannel", summary = "Redirects to the door's channel in Discord, for a member")
    @ApiResponse(responseCode = "302", description = "To the channel", content = [Content()])
    @ApiResponse(responseCode = "404", description = "No such door", content = [Content()])
    fun channel(
        @Parameter(schema = Schema(allowableValues = ["welcome", "board", "suggestions"]))
        @PathVariable door: String,
    ): ResponseEntity<Void> = redirect(DiscordDoor.of(door)?.let(doors::channel))

    private fun redirect(to: String?): ResponseEntity<Void> =
        to?.let {
            ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI(it))
                .cacheControl(CacheControl.maxAge(BROWSER_CACHE).cachePublic())
                .build()
        } ?: ResponseEntity.notFound().build()

    private companion object {
        /* An invite is permanent; a few minutes keeps a renamed channel from lingering long. */
        val BROWSER_CACHE: Duration = Duration.ofMinutes(5)
    }
}
