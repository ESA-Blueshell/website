package net.blueshell.api.cohort.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.cohort.domain.AdoptionMatch
import net.blueshell.api.cohort.domain.CohortDiscord
import net.blueshell.api.cohort.domain.DiscordAdoption
import net.blueshell.api.cohort.domain.DiscordChoice
import net.blueshell.api.cohort.domain.DiscordPlace
import net.blueshell.api.cohort.domain.RefusedMatch
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.security.BoardOnly
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@Schema(name = "DiscordPlaceRequest", description = "The role a cohort's people hold, and the channels it opens.")
data class DiscordPlaceRequest(
    @param:Schema(description = "An existing role to link, where there is none yet")
    val roleId: String? = null,
    @param:Schema(description = "Make a new role, where there is none yet and no role is named")
    val createRole: Boolean = false,
    @param:Schema(description = "Every channel the role opens; one left out is closed to it")
    val channelIds: List<String> = emptyList(),
    @param:Schema(description = "A new private channel to make for the role, by name")
    val createChannel: String? = null,
) {
    fun choice() = DiscordChoice(roleId, createRole, channelIds, createChannel)
}

@Schema(name = "AdoptDiscord", description = "The matches the board confirmed, by their cohort's key")
data class AdoptDiscordRequest(
    val keys: List<String>,
)

@Schema(name = "AdoptedDiscord")
data class AdoptDiscordResponse(
    val linked: Int,
    @param:Schema(description = "The confirmed matches Discord refused, each as its name and why")
    val refused: List<RefusedMatch>,
)

/** A committee's, a team's and a board's role and private channels on Discord. */
@RestController
@Tag(name = "Discord places", description = "A committee's, a team's or a board's role and private channels")
@BoardOnly
class DiscordPlaceController(
    private val discord: CohortDiscord,
    private val adoption: DiscordAdoption,
    @param:Value($$"${discord.committees-category:Committees}") private val committees: String,
    @param:Value($$"${discord.esports-category:Esports}") private val esports: String,
    @param:Value($$"${discord.board-category:Board}") private val boards: String,
) {
    @GetMapping("/management/committees/{id}/discord")
    fun findCommitteeDiscord(
        @PathVariable id: Long,
    ): DiscordPlace = discord.read(committee(id))

    @PutMapping("/management/committees/{id}/discord")
    fun setCommitteeDiscord(
        @PathVariable id: Long,
        @RequestBody request: DiscordPlaceRequest,
    ): DiscordPlace = discord.apply(committee(id), request.choice(), committees)

    @GetMapping("/management/teams/{id}/discord")
    fun findTeamDiscord(
        @PathVariable id: Long,
    ): DiscordPlace = discord.read(team(id))

    @PutMapping("/management/teams/{id}/discord")
    fun setTeamDiscord(
        @PathVariable id: Long,
        @RequestBody request: DiscordPlaceRequest,
    ): DiscordPlace = discord.apply(team(id), request.choice(), esports)

    /** Takes the team's role and private channel off Discord: the board's explicit removal. */
    @DeleteMapping("/management/teams/{id}/discord")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeTeamDiscord(
        @PathVariable id: Long,
    ) = discord.remove(team(id))

    /** Takes the role off the cohort that follows it. Discord keeps the role, its holders and its channels. */
    @GetMapping("/management/boards/discord/{key}")
    fun findBoardDiscord(
        @PathVariable key: String,
    ): DiscordPlace = discord.read(board(key))

    @PutMapping("/management/boards/discord/{key}")
    fun setBoardDiscord(
        @PathVariable key: String,
        @RequestBody request: DiscordPlaceRequest,
    ): DiscordPlace = discord.apply(board(key), request.choice(), boards)

    @DeleteMapping("/management/discord/roles/{roleId}/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unlinkDiscordRole(
        @PathVariable roleId: String,
    ) = discord.unlink(roleId)

    /** Committees and teams with no role yet, matched by name to the roles and channels already in the server. */
    @GetMapping("/management/discord/adoption")
    fun listDiscordMatches(): List<AdoptionMatch> = adoption.proposals()

    /** Links the matches the board confirmed, by their cohort's key. */
    @PostMapping("/management/discord/adoption")
    fun adoptDiscordMatches(
        @RequestBody request: AdoptDiscordRequest,
    ): AdoptDiscordResponse = adoption.adopt(request.keys).let { AdoptDiscordResponse(it.linked, it.refused) }

    private fun committee(id: Long) = "${CohortType.COMMITTEE_MEMBERS}:$id"

    private fun team(id: Long) = "${CohortType.TEAM_PLAYERS}:$id"

    // A board's people are one of three cohorts by where it stands: in office, candidate, or its own years.
    private fun board(key: String) =
        key.takeIf { it == CohortType.BOARD.name || it == CohortType.KANDI.name || it.startsWith("${CohortType.BOARD_YEAR_MEMBERS}:") }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No board is kept under $key")
}
