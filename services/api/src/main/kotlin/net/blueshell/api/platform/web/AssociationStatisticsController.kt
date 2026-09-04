package net.blueshell.api.platform.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import net.blueshell.api.platform.statistics.AssociationStatistics
import net.blueshell.api.platform.statistics.AssociationStatisticsReader
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@Schema(
    name = "AssociationStatisticsResponse",
    description = "What the association can say about itself in numbers",
)
data class AssociationStatisticsResponse(
    @Schema(description = "Games with a team standing in them now")
    val gamesPlayed: Long,
    @Schema(description = "Seasons that had a team fielded in them")
    val seasonsPlayed: Long,
    @Schema(description = "Committees that run")
    val committees: Long,
    @Schema(description = "Boards that have sat")
    val boards: Long,
    @Schema(description = "Teams standing this season")
    val teamsThisSeason: Long,
    @Schema(description = "Events over the last rolling year")
    val eventsLastYear: Long,
)

@RestController
@Tag(name = "Statistics")
class AssociationStatisticsController(private val reader: AssociationStatisticsReader) {

    /**
     * The association's own numbers, in one read.
     *
     * Answers an anonymous caller, which is what asks for these. The event count is the one
     * that caller may see, which is fewer than the events list shows a board member — the same
     * rule, not a different number.
     */
    @GetMapping("/statistics/association")
    @PermitAll
    fun associationStatistics(): AssociationStatisticsResponse = reader.read().asResponse()
}

private fun AssociationStatistics.asResponse() = AssociationStatisticsResponse(
    gamesPlayed = gamesPlayed,
    seasonsPlayed = seasonsPlayed,
    committees = committees,
    boards = boards,
    teamsThisSeason = teamsThisSeason,
    eventsLastYear = eventsLastYear,
)
