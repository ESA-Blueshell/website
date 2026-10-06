package net.blueshell.api.cohort.domain

import net.blueshell.api.board.api.BoardMemberService
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.esports.api.TeamRosterService
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.api.UserService
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * The active members: everybody holding the COMMITTEE role, which follows a seat on any committee,
 * and everybody on an esports team's line-up in the season fielded now.
 */
class ActivistsDefinition(
    private val users: UserService,
    private val rosters: TeamRosterService,
) : CohortDefinition {
    override val key = CohortType.ACTIVISTS.name
    override val type = CohortType.ACTIVISTS
    override val scope = null
    override val label = "Activists"
    override val folder = CohortFolders.ACTIVISTS

    override fun members(): Set<Long> =
        users.findIdsHolding(Role.COMMITTEE) + rosters.teamNames().keys.flatMapTo(mutableSetOf()) { rosters.currentPlayersOf(it) }

    override fun contains(userId: Long): Boolean = userId in members()
}

@Component
class ActivistsProvider(
    private val users: UserService,
    private val rosters: TeamRosterService,
) : CohortDefinitionProvider {
    override val type = CohortType.ACTIVISTS

    override fun definitions(): List<CohortDefinition> = listOf(ActivistsDefinition(users, rosters))
}

/** Everybody holding the MEMBER role, which follows an active membership: the api's own answer to who is a member. */
class CurrentMembersDefinition(
    private val users: UserService,
) : CohortDefinition {
    override val key = CohortType.CURRENT_MEMBERS.name
    override val type = CohortType.CURRENT_MEMBERS
    override val scope = null
    override val label = "Members"
    override val folder = CohortFolders.MEMBERS

    override fun members(): Set<Long> = users.findIdsHolding(Role.MEMBER)

    override fun contains(userId: Long): Boolean = userId in members()
}

@Component
class CurrentMembersProvider(
    private val users: UserService,
) : CohortDefinitionProvider {
    override val type = CohortType.CURRENT_MEMBERS

    override fun definitions(): List<CohortDefinition> = listOf(CurrentMembersDefinition(users))
}

/**
 * Everybody holding the COMMITTEE role, which follows a seat on any committee. Unlike the activists
 * it counts One-Of-Committee seats and leaves out a board member with no committee seat.
 */
class CurrentCommitteeMembersDefinition(
    private val users: UserService,
) : CohortDefinition {
    override val key = CohortType.CURRENT_COMMITTEE_MEMBERS.name
    override val type = CohortType.CURRENT_COMMITTEE_MEMBERS
    override val scope = null
    override val label = "Committee members"
    override val folder = CohortFolders.COMMITTEES

    override fun members(): Set<Long> = users.findIdsHolding(Role.COMMITTEE)

    override fun contains(userId: Long): Boolean = userId in members()
}

@Component
class CurrentCommitteeMembersProvider(
    private val users: UserService,
) : CohortDefinitionProvider {
    override val type = CohortType.CURRENT_COMMITTEE_MEMBERS

    override fun definitions(): List<CohortDefinition> = listOf(CurrentCommitteeMembersDefinition(users))
}

/** The board in office today: everybody whose place covers today, on a board that has taken office. */
class BoardDefinition(
    private val boardMembers: BoardMemberService,
) : CohortDefinition {
    override val key = CohortType.BOARD.name
    override val type = CohortType.BOARD
    override val scope = null
    override val label = "Board"
    override val folder = CohortFolders.BOARD

    override fun members(): Set<Long> = boardMembers.servingOn(LocalDate.now())

    override fun contains(userId: Long): Boolean = userId in members()
}

/** Kandi: everybody on a board that has not taken office yet. On its first day they move to the board. */
class KandiDefinition(
    private val boardMembers: BoardMemberService,
) : CohortDefinition {
    override val key = CohortType.KANDI.name
    override val type = CohortType.KANDI
    override val scope = null
    override val label = "Kandi"
    override val folder = CohortFolders.BOARD

    override fun members(): Set<Long> = boardMembers.candidatesOn(LocalDate.now())

    override fun contains(userId: Long): Boolean = userId in members()
}

@Component
class BoardProvider(
    private val boardMembers: BoardMemberService,
) : CohortDefinitionProvider {
    override val type = CohortType.BOARD

    override fun definitions(): List<CohortDefinition> = listOf(BoardDefinition(boardMembers))
}

@Component
class KandiProvider(
    private val boardMembers: BoardMemberService,
) : CohortDefinitionProvider {
    override val type = CohortType.KANDI

    override fun definitions(): List<CohortDefinition> = listOf(KandiDefinition(boardMembers))
}
