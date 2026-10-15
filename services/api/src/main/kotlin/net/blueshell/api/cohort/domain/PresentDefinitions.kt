package net.blueshell.api.cohort.domain

import net.blueshell.api.board.api.BoardMemberService
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.committee.api.CommitteeMemberService
import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.api.UserService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

/**
 * Everybody holding a seat today: on the board, or on a running committee other than
 * One-Of-Committee, whose seats are for a single event.
 */
class ActivistsDefinition(
    private val committees: CommitteeService,
    private val committeeMembers: CommitteeMemberService,
    private val boardMembers: BoardMemberService,
) : CohortDefinition {
    override val key = CohortType.ACTIVISTS.name
    override val type = CohortType.ACTIVISTS
    override val scope = null
    override val label = "Activists"
    override val folder = CohortFolders.ACTIVISTS

    override fun members(): Set<Long> {
        val today = LocalDate.now()
        val seated = committees.findAll().filter(::counts).flatMapTo(mutableSetOf()) { committeeMembers.findUserIdsOnCommittee(it.id!!) }
        return seated + boardMembers.serversBetween(today, today)
    }

    override fun contains(userId: Long): Boolean {
        val today = LocalDate.now()
        return boardMembers.servedBetween(userId, today, today) || committees.findAllByUserId(userId).any(::counts)
    }

    // The seed names it "One-Of-Committee (Member's Iniative)", so the name is matched by its start.
    private fun counts(committee: Committee) = !committee.archived && !committee.name.startsWith(ONE_OF_COMMITTEE, ignoreCase = true)

    private companion object {
        const val ONE_OF_COMMITTEE = "One-Of-Committee"
    }
}

@Component
class ActivistsProvider(
    private val committees: CommitteeService,
    private val committeeMembers: CommitteeMemberService,
    private val boardMembers: BoardMemberService,
) : CohortDefinitionProvider {
    override val type = CohortType.ACTIVISTS

    override fun definitions(): List<CohortDefinition> = listOf(ActivistsDefinition(committees, committeeMembers, boardMembers))
}

/**
 * Everybody holding the member role itself, which follows a paid membership. A board member or an
 * admin inherits what a member may do, but is only in this cohort with a membership of their own.
 */
class CurrentMembersDefinition(
    private val users: UserService,
) : CohortDefinition {
    override val key = CohortType.CURRENT_MEMBERS.name
    override val type = CohortType.CURRENT_MEMBERS
    override val scope = null
    override val label = "Members"
    override val folder = CohortFolders.MEMBERS

    override fun members(): Set<Long> = users.findIdsHoldingItself(Role.MEMBER)

    // Only a user who is gone is no member: a failed read must not take somebody's role away.
    override fun contains(userId: Long): Boolean =
        try {
            Role.MEMBER in users.findById(userId).roles
        } catch (e: ResponseStatusException) {
            if (e.statusCode != HttpStatus.NOT_FOUND) throw e
            false
        }
}

@Component
class CurrentMembersProvider(
    private val users: UserService,
) : CohortDefinitionProvider {
    override val type = CohortType.CURRENT_MEMBERS

    override fun definitions(): List<CohortDefinition> = listOf(CurrentMembersDefinition(users))
}
