package net.blueshell.api.platform.statistics

import net.blueshell.api.board.api.BoardCounts
import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.esports.api.EsportsCounts
import net.blueshell.api.event.api.EventService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * What the association can say about itself in numbers.
 *
 * Every number here is derived from records that are already published, so a caller states a
 * fact rather than a claim someone has to remember to update. What is absent is as deliberate:
 * there is no member count, because reading members needs a permission an anonymous caller
 * does not have.
 */
data class AssociationStatistics(
    /** Games with a team standing in them now. */
    val gamesPlayed: Long,
    /** Seasons that had a team fielded in them. */
    val seasonsPlayed: Long,
    val committees: Long,
    val boards: Long,
    val teamsThisSeason: Long,
    /** Events over the last rolling year, which divides into a rate. */
    val eventsLastYear: Long,
)

/**
 * Reads the association's numbers, each from the module that owns the records behind it.
 *
 * A reader rather than a module of its own: nothing here decides anything, and the rules that
 * make a number mean something — which season counts as now, which events a caller may see —
 * stay with the records they are about.
 */
@Service
class AssociationStatisticsReader(
    private val committees: CommitteeService,
    private val boards: BoardCounts,
    private val esports: EsportsCounts,
    private val events: EventService,
) {
    /** [now] is a parameter so a test can say when it is, the way the esports reads do. */
    @Transactional(readOnly = true)
    fun read(now: LocalDateTime = LocalDateTime.now()): AssociationStatistics {
        return AssociationStatistics(
            gamesPlayed = esports.gamesCurrentlyPlayed(),
            seasonsPlayed = esports.seasonsPlayed(),
            committees = committees.count(),
            boards = boards.count(),
            teamsThisSeason = esports.teamsFieldedNow(),
            // A rolling year rather than an academic one: the number answers "how often
            // does something happen here", and an academic year read in September says almost
            // nothing happens.
            eventsLastYear = events.countBetween(now.minusYears(1), now),
        )
    }
}
