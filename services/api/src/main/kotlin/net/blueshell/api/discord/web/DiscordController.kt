package net.blueshell.api.discord.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import net.blueshell.api.discord.domain.DiscordLiveService
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/**
 * The live Discord band's one read. Public: it says only what the server shows anybody who opens
 * it. Answered from the gateway's memory, so a page view never becomes a Discord call. A 503 means
 * the bot is not set up or not connected, and the band falls back to Discord's public widget. The
 * band follows changes over [DiscordLiveSocket] and reads this where the socket will not open.
 */
@Tag(name = "Discord")
@RestController
@RequestMapping("/discord")
class DiscordController(
    private val discordLiveService: DiscordLiveService,
) {
    private companion object {
        /* Short enough that a join shows within the band's own refresh, long enough to spare the api. */
        val BROWSER_CACHE: Duration = Duration.ofSeconds(15)
    }

    @PermitAll
    @GetMapping("/live")
    @Operation(operationId = "readDiscordLive", summary = "The Discord server's counts and voice rooms, with who is in them")
    // Both answers stated: naming the 503 alone would leave the 200 without its schema.
    @ApiResponse(
        responseCode = "200",
        content = [Content(schema = Schema(implementation = DiscordLiveResponse::class))],
    )
    @ApiResponse(responseCode = "503", description = "The bot is not set up, or not connected yet", content = [Content()])
    fun live(): ResponseEntity<DiscordLiveResponse> {
        val live = discordLiveService.live() ?: return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.maxAge(BROWSER_CACHE).cachePublic())
            .body(live.toResponse())
    }
}
