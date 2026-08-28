package net.blueshell.api.platform.integration.cohort.application.definition

import net.blueshell.api.board.api.BoardMemberService
import net.blueshell.api.domain.committee.application.CommitteeMemberService
import net.blueshell.api.domain.esports.application.TeamRosterService
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * One way of being active in the association for a stretch of time.
 *
 * Being active is not one thing: a member can sit on a committee, sit on a board, or play for
 * a team, and any of the three counts. Each is asked separately so that adding a fourth is a
 * new bean rather than an edit to the rule that unions them — and so that a source whose data
 * does not exist yet simply is not registered.
 */
interface PeriodActivitySource {
    /** Everybody this source counts as active during the window. */
    fun activeBetween(from: LocalDate, to: LocalDate): Set<Long>

    /** Whether this one member was active during the window, by this source's reckoning. */
    fun wasActive(userId: Long, from: LocalDate, to: LocalDate): Boolean
}

/** A seat on a committee, including seats since given up. */
@Component
class CommitteeSeatActivity(
    private val committeeMembers: CommitteeMemberService,
) : PeriodActivitySource {
    override fun activeBetween(from: LocalDate, to: LocalDate): Set<Long> =
        committeeMembers.findUserIdsSeatedBetween(from.atStartOfDay().toInstant(ZoneOffset.UTC), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC))

    override fun wasActive(userId: Long, from: LocalDate, to: LocalDate): Boolean {
        val start = from.atStartOfDay().toInstant(ZoneOffset.UTC)
        val end = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        return committeeMembers.findMembershipWindowsForUser(userId)
            .any { it.joinedAt.isBefore(end) && it.leftAt.isAfter(start) }
    }
}

/** A seat on a board. */
@Component
class BoardSeatActivity(
    private val boardMembers: BoardMemberService,
) : PeriodActivitySource {
    override fun activeBetween(from: LocalDate, to: LocalDate): Set<Long> =
        boardMembers.serversBetween(from, to)

    override fun wasActive(userId: Long, from: LocalDate, to: LocalDate): Boolean =
        boardMembers.servedBetween(userId, from, to)
}

/** A place on a team's roster, in a season that overlapped the window. */
@Component
class TeamRosterActivity(
    private val rosters: TeamRosterService,
) : PeriodActivitySource {
    override fun activeBetween(from: LocalDate, to: LocalDate): Set<Long> =
        rosters.playersBetween(from, to)

    override fun wasActive(userId: Long, from: LocalDate, to: LocalDate): Boolean =
        rosters.playedBetween(userId, from, to)
}
