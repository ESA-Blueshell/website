package net.blueshell.api.pinger.web

import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import net.blueshell.api.pinger.api.LeaderboardService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * The public contribution leaderboard and its live stream.
 *
 * Reading the board and its stream is open to anyone; every contributor appears, shown by their
 * public identity, and SiteCie is a labelled house line outside the ranking. There is no opt-in:
 * contributing is the permission.
 */
@RestController
@RequestMapping("/pinger/leaderboard")
@Tag(name = "Pinger leaderboard", description = "The public contribution leaderboard and its live stream")
class LeaderboardController(
    private val leaderboard: LeaderboardService,
    private val stream: LeaderboardStream,
) {
    @PermitAll
    @GetMapping
    fun board(): LeaderboardResponse = LeaderboardResponse.from(leaderboard.snapshot())

    @PermitAll
    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @ApiResponse(
        responseCode = "200",
        content = [Content(schema = Schema(implementation = LeaderboardResponse::class))],
    )
    fun stream(): SseEmitter = stream.open()
}
