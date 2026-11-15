package net.blueshell.api.vote.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.security.BoardOnly
import net.blueshell.api.vote.domain.AnnualVotes
import net.blueshell.api.vote.persistence.VoteKind
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** The votes themselves: anybody reads where one stands, and the board sets its dates. */
@RestController
@Validated
@RequestMapping("/annual-votes/{kind}")
@Tag(name = "Annual vote")
class AnnualVoteController(
    private val votes: AnnualVotes,
) {
    @PermitAll
    @GetMapping("/current")
    fun currentAnnualVote(
        @PathVariable kind: VoteKind,
    ): AnnualVoteResponse = votes.current(kind).asResponse()

    @BoardOnly
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createAnnualVote(
        @PathVariable kind: VoteKind,
        @Valid @RequestBody request: CreateAnnualVoteRequest,
    ): AnnualVoteResponse = votes.create(kind, request.year, request.opensAt, request.closesAt, request.revealAt).asResponse()

    @BoardOnly
    @PutMapping("/{year}")
    fun scheduleAnnualVote(
        @PathVariable kind: VoteKind,
        @PathVariable year: Int,
        @Valid @RequestBody request: ScheduleAnnualVoteRequest,
    ): AnnualVoteResponse = votes.reschedule(kind, year, request.opensAt, request.closesAt, request.revealAt).asResponse()

    @BoardOnly
    @GetMapping("/{year}/standings")
    fun annualVoteStandings(
        @PathVariable kind: VoteKind,
        @PathVariable year: Int,
    ): List<VotePlacingResponse> = votes.standings(kind, year).map { it.asResponse() }
}
