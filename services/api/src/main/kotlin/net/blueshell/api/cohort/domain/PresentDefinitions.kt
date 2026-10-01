package net.blueshell.api.cohort.domain

import net.blueshell.api.board.api.BoardMemberService
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.committee.api.CommitteeMemberService
import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.user.api.MembershipService
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * Everybody holding a seat today: on any committee but One-Of-Committee, or on the board. Read
 * from what is true now, so a seat given up leaves the cohort at once.
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
        val seated =
            committees
                .findAll()
                .filter { it.id != null && !it.archived && !it.name.equals(ONE_OF_COMMITTEE, ignoreCase = true) }
                .flatMapTo(mutableSetOf()) { committeeMembers.findUserIdsOnCommittee(it.id!!) }
        return seated + boardMembers.serversBetween(today, today)
    }

    override fun contains(userId: Long): Boolean = userId in members()

    companion object {
        /** Seats for a single one-off event, which do not make somebody an activist. */
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

/** Everybody whose active membership covers today; a pending one waits for its first contribution. */
class CurrentMembersDefinition(
    private val memberships: MembershipService,
) : CohortDefinition {
    override val key = CohortType.CURRENT_MEMBERS.name
    override val type = CohortType.CURRENT_MEMBERS
    override val scope = null
    override val label = "Members"
    override val folder = CohortFolders.MEMBERS

    override fun members(): Set<Long> = memberships.findActiveUserIdsOn(LocalDate.now())

    override fun contains(userId: Long): Boolean {
        val today = LocalDate.now()
        return memberships.findByUserId(userId).any {
            it.activatedOn != null && !it.startDate.isAfter(today) && it.endDate?.isBefore(today) != true
        }
    }
}

@Component
class CurrentMembersProvider(
    private val memberships: MembershipService,
) : CohortDefinitionProvider {
    override val type = CohortType.CURRENT_MEMBERS

    override fun definitions(): List<CohortDefinition> = listOf(CurrentMembersDefinition(memberships))
}
