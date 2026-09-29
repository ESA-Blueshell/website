package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortMember
import net.blueshell.api.cohort.persistence.CohortMemberRepository
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.DriftResolution
import net.blueshell.api.cohort.persistence.DriftResolutionAction
import net.blueshell.api.cohort.persistence.DriftResolutionRepository
import net.blueshell.api.cohort.persistence.state
import net.blueshell.api.shared.enums.CohortMemberState
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.tracking.ActorProvider
import net.blueshell.api.sync.api.ExternalIdConflictException
import net.blueshell.api.user.api.UserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * The board's answers to drift on one target: push people we hold to it, remove people only it
 * holds, or link those to an account. Each person resolved is recorded with who resolved them.
 * Adopting is [InboundReconcile], which records through [record].
 */
@Service
class DriftResolutions(
    private val cohorts: CohortRepository,
    private val members: CohortMemberRepository,
    private val resolutions: DriftResolutionRepository,
    private val remediation: CohortRemediation,
    private val users: UserService,
    private val jobs: JobQueue,
    private val actors: ActorProvider,
) {
    /** Queues a push of each ours-only person in [userIds]; anyone already on the target is skipped. */
    @Transactional
    fun push(
        subjectId: Long,
        cohortId: Long,
        userIds: Collection<Long>,
    ): Int {
        target(subjectId, cohortId)
        val oursOnly =
            members
                .findAllByCohortIdAndUserIdIsNotNull(cohortId)
                .filter { it.userId in userIds && it.state in OURS_ONLY }
        oursOnly.forEach { row ->
            jobs.runAsync(
                CohortJobs.SyncCohortMembership,
                CohortJobs.SyncCohortMembershipPayload(row.userId!!, cohortId, SyncCohortMembershipIntent.ADD),
                JobTrigger.SITE_ACTION,
            )
        }
        record(cohortId, DriftResolutionAction.PUSH, oursOnly.map { it.person() })
        return oursOnly.size
    }

    /** Queues the removal of each theirs-only person in [externalUserIds] from the target. */
    @Transactional
    fun remove(
        subjectId: Long,
        cohortId: Long,
        externalUserIds: Collection<String>,
    ): Int {
        target(subjectId, cohortId)
        val theirsOnly = members.findAllByCohortIdAndExternalUserIdInAndUserIdIsNull(cohortId, externalUserIds)
        theirsOnly.forEach { row ->
            jobs.runAsync(
                CohortJobs.RemoveExternalMember,
                CohortJobs.RemoveExternalMemberPayload(cohortId, row.externalUserId!!),
                JobTrigger.SITE_ACTION,
            )
        }
        record(cohortId, DriftResolutionAction.REMOVE, theirsOnly.map { it.person() })
        return theirsOnly.size
    }

    /** For each theirs-only person in [externalUserIds], the account whose address the target calls them by. */
    @Transactional(readOnly = true)
    fun proposeLinks(
        subjectId: Long,
        cohortId: Long,
        externalUserIds: Collection<String>,
    ): List<LinkProposal> {
        target(subjectId, cohortId)
        val theirsOnly = members.findAllByCohortIdAndExternalUserIdInAndUserIdIsNull(cohortId, externalUserIds)
        val byEmail = users.findAllByEmails(theirsOnly.mapNotNull { it.label }).associateBy { it.email.lowercase() }
        return theirsOnly.map { row ->
            val user = row.label?.let { byEmail[it.trim().lowercase()] }
            LinkProposal(row.externalUserId!!, row.label, user?.id, user?.fullName)
        }
    }

    /**
     * Links each theirs-only contact to its account; a contact already another account's is
     * reported, not linked. Each link is its own transaction, so one conflict does not roll back
     * the rest.
     */
    fun link(
        subjectId: Long,
        cohortId: Long,
        links: List<LinkChoice>,
    ): LinkOutcome {
        val cohort = target(subjectId, cohortId)
        val system = TargetSystem.valueOf(cohort.system)
        val labels =
            members
                .findAllByCohortIdAndExternalUserIdInAndUserIdIsNull(cohortId, links.map { it.externalUserId })
                .associate { it.externalUserId!! to it.label }
        val linked = mutableListOf<Person>()
        val conflicts = mutableListOf<LinkConflict>()
        links.forEach { choice ->
            try {
                remediation.linkUser(subjectId, choice.userId, system, choice.externalUserId)
                linked += Person(choice.userId, choice.externalUserId, labels[choice.externalUserId])
            } catch (conflict: ExternalIdConflictException) {
                conflicts += LinkConflict(choice.externalUserId, conflict.existingUserId)
            }
        }
        record(cohortId, DriftResolutionAction.LINK, linked)
        return LinkOutcome(linked.size, conflicts)
    }

    /** Switches whether each reconcile of the target removes its theirs-only people. */
    @Transactional
    fun enforce(
        subjectId: Long,
        cohortId: Long,
        enforced: Boolean,
    ) {
        target(subjectId, cohortId).enforced = enforced
    }

    /** Records [people] as resolved by [action] on [cohortId], by whoever is acting now. */
    fun record(
        cohortId: Long,
        action: DriftResolutionAction,
        people: List<Person>,
    ) {
        if (people.isEmpty()) return
        val by = actors.currentOrSystem().userId
        val at = Instant.now()
        resolutions.saveAll(people.map { DriftResolution(cohortId, action, it.userId, it.externalUserId, it.label, by, at) })
    }

    private fun target(
        subjectId: Long,
        cohortId: Long,
    ): Cohort {
        val cohort = cohorts.findById(cohortId).orElse(null)
        if (cohort == null || cohort.subjectId != subjectId) throw TargetNotOfCohort(cohortId)
        if (cohort.externalId.isNullOrBlank()) throw TargetNotCreated(cohortId)
        return cohort
    }

    private fun CohortMember.person() = Person(userId, externalUserId, label)

    data class Person(
        val userId: Long?,
        val externalUserId: String?,
        val label: String?,
    )

    private companion object {
        val OURS_ONLY = setOf(CohortMemberState.DESIRED, CohortMemberState.SYNCED)
    }
}

/** A theirs-only contact and the account with its address, where there is one. */
data class LinkProposal(
    val externalUserId: String,
    val label: String?,
    val userId: Long?,
    val userFullName: String?,
)

data class LinkChoice(
    val externalUserId: String,
    val userId: Long,
)

/** A contact that could not be linked because another account already holds it. */
data class LinkConflict(
    val externalUserId: String,
    val existingUserId: Long,
)

data class LinkOutcome(
    val linked: Int,
    val conflicts: List<LinkConflict>,
)
