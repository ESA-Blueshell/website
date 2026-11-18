package net.blueshell.api.vote.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.security.MemberOnly
import net.blueshell.api.shared.security.UserPrincipal
import net.blueshell.api.vote.domain.VoteBallotUseCases
import net.blueshell.api.vote.persistence.VoteKind
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** A member's own ballot for a vote. */
@RestController
@Validated
@RequestMapping("/annual-votes/{kind}/{year}/ballot")
@Tag(name = "Annual vote")
@MemberOnly
class VoteBallotController(
    private val ballots: VoteBallotUseCases,
) {
    @GetMapping
    fun myVoteBallot(
        @PathVariable kind: VoteKind,
        @PathVariable year: Int,
        @AuthenticationPrincipal voter: UserPrincipal,
    ): VoteBallotResponse = ballots.mine(kind, year, voter.id).asResponse()

    @PutMapping
    fun castVoteBallot(
        @PathVariable kind: VoteKind,
        @PathVariable year: Int,
        @Valid @RequestBody request: CastVoteBallotRequest,
        @AuthenticationPrincipal voter: UserPrincipal,
    ): VoteBallotResponse = ballots.cast(kind, year, voter.id, request.picks.map { it.choice() }, request.version).asResponse()
}
