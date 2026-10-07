package net.blueshell.api.pinger.web

import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import net.blueshell.api.pinger.api.LeaderboardOptIns
import net.blueshell.api.pinger.api.LeaderboardService
import net.blueshell.api.security.MemberOnly
import net.blueshell.api.shared.security.CurrentUserProvider
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * The public contribution leaderboard and a member's own opt-in to it.
 *
 * Reading the board and its live stream is open to anyone; only opted-in members appear, and
 * SiteCie is a labelled house line outside the ranking. A member reads and sets their own opt-in,
 * which defaults to off.
 */
@RestController
@RequestMapping("/pinger/leaderboard")
@Tag(name = "Pinger leaderboard", description = "The public contribution leaderboard and a member's opt-in")
class LeaderboardController(
    private val leaderboard: LeaderboardService,
    private val optIns: LeaderboardOptIns,
    private val stream: LeaderboardStream,
    private val currentUser: CurrentUserProvider,
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

    @MemberOnly
    @GetMapping("/opt-in")
    fun optIn(): OptInResponse = OptInResponse(optIns.isOptedIn(memberId()))

    @MemberOnly
    @PutMapping("/opt-in")
    fun setOptIn(
        @RequestBody request: OptInRequest,
    ): OptInResponse {
        optIns.setOptedIn(memberId(), request.optedIn)
        return OptInResponse(request.optedIn)
    }

    private fun memberId(): Long =
        currentUser.currentUser()?.id
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
}
