package net.blueshell.api.vote.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.security.MemberOnly
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.vote.domain.VoteBallots
import net.blueshell.api.vote.persistence.VoteKind
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/** A member's own ballot for a vote. */
@RestController
@Validated
@RequestMapping("/annual-votes/{kind}/{year}/ballot")
@Tag(name = "Annual vote")
@MemberOnly
class VoteBallotController(
    private val ballots: VoteBallots,
    private val currentUser: CurrentUserProvider,
) {
    @GetMapping
    fun myVoteBallot(
        @PathVariable kind: VoteKind,
        @PathVariable year: Int,
    ): VoteBallotResponse = ballots.mine(kind, year, voter()).asResponse()

    @PutMapping
    fun castVoteBallot(
        @PathVariable kind: VoteKind,
        @PathVariable year: Int,
        @Valid @RequestBody request: CastVoteBallotRequest,
    ): VoteBallotResponse = ballots.cast(kind, year, voter(), request.picks.map { it.choice() }, request.version).asResponse()

    private fun voter(): Long = currentUser.currentUser()?.id ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
}
