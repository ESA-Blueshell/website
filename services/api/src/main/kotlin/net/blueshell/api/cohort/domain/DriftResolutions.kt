package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.DriftResolution
import net.blueshell.api.cohort.persistence.DriftResolutionAction
import net.blueshell.api.cohort.persistence.DriftResolutionRepository
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetMember
import net.blueshell.api.cohort.persistence.TargetMemberRepository
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.cohort.persistence.state
import net.blueshell.api.shared.enums.TargetMemberState
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
    private val targets: TargetRepository,
    private val members: TargetMemberRepository,
    private val resolutions: DriftResolutionRepository,
    private val remediation: CohortRemediation,
    private val users: UserService,
    private val jobs: JobQueue,
    private val actors: ActorProvider,
) {
    /** Queues a push of each ours-only person in [userIds]; anyone already on the target is skipped. */
    @Transactional
    fun push(
        cohortId: Long,
        targetId: Long,
        userIds: Collection<Long>,
    ): Int {
        target(cohortId, targetId)
        val oursOnly =
            members
                .findAllByTargetIdAndUserIdIsNotNull(targetId)
                .filter { it.userId in userIds && it.state in OURS_ONLY }
        oursOnly.forEach { row ->
            jobs.runAsync(
                CohortJobs.SyncCohortMembership,
                CohortJobs.SyncCohortMembershipPayload(row.userId!!, targetId, SyncCohortMembershipIntent.ADD),
                JobTrigger.SITE_ACTION,
            )
        }
        record(targetId, DriftResolutionAction.PUSH, oursOnly.map { it.person() })
        return oursOnly.size
    }

    /** Queues the removal of each theirs-only person in [externalUserIds] from the target. */
    @Transactional
    fun remove(
        cohortId: Long,
        targetId: Long,
        externalUserIds: Collection<String>,
    ): Int {
        target(cohortId, targetId)
        val theirsOnly = members.findAllByTargetIdAndExternalUserIdInAndUserIdIsNull(targetId, externalUserIds)
        theirsOnly.forEach { row ->
            jobs.runAsync(
                CohortJobs.RemoveExternalMember,
                CohortJobs.RemoveExternalMemberPayload(targetId, row.externalUserId!!),
                JobTrigger.SITE_ACTION,
            )
        }
        record(targetId, DriftResolutionAction.REMOVE, theirsOnly.map { it.person() })
        return theirsOnly.size
    }

    /** For each theirs-only person in [externalUserIds], the account whose address the target calls them by. */
    @Transactional(readOnly = true)
    fun proposeLinks(
        cohortId: Long,
        targetId: Long,
        externalUserIds: Collection<String>,
    ): List<LinkProposal> {
        target(cohortId, targetId)
        val theirsOnly = members.findAllByTargetIdAndExternalUserIdInAndUserIdIsNull(targetId, externalUserIds)
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
        cohortId: Long,
        targetId: Long,
        links: List<LinkChoice>,
    ): LinkOutcome {
        val target = target(cohortId, targetId)
        val system = TargetSystem.valueOf(target.system)
        val labels =
            members
                .findAllByTargetIdAndExternalUserIdInAndUserIdIsNull(targetId, links.map { it.externalUserId })
                .associate { it.externalUserId!! to it.label }
        val linked = mutableListOf<Person>()
        val conflicts = mutableListOf<LinkConflict>()
        links.forEach { choice ->
            try {
                remediation.linkUser(cohortId, choice.userId, system, choice.externalUserId)
                linked += Person(choice.userId, choice.externalUserId, labels[choice.externalUserId])
            } catch (conflict: ExternalIdConflictException) {
                conflicts += LinkConflict(choice.externalUserId, conflict.existingUserId)
            }
        }
        record(targetId, DriftResolutionAction.LINK, linked)
        return LinkOutcome(linked.size, conflicts)
    }

    /** Switches whether each reconcile of the target removes its theirs-only people. */
    @Transactional
    fun enforce(
        cohortId: Long,
        targetId: Long,
        enforced: Boolean,
    ) {
        target(cohortId, targetId).enforced = enforced
    }

    /** Records [people] as resolved by [action] on [cohortId], by whoever is acting now. */
    fun record(
        targetId: Long,
        action: DriftResolutionAction,
        people: List<Person>,
    ) {
        if (people.isEmpty()) return
        val by = actors.currentOrSystem().userId
        val at = Instant.now()
        resolutions.saveAll(people.map { DriftResolution(targetId, action, it.userId, it.externalUserId, it.label, by, at) })
    }

    private fun target(
        cohortId: Long,
        targetId: Long,
    ): Target {
        val target = targets.findById(targetId).orElse(null)
        if (target == null || target.cohortId != cohortId) throw TargetNotOfCohort(targetId)
        if (target.externalId.isNullOrBlank()) throw TargetNotCreated(targetId)
        return target
    }

    private fun TargetMember.person() = Person(userId, externalUserId, label)

    data class Person(
        val userId: Long?,
        val externalUserId: String?,
        val label: String?,
    )

    private companion object {
        val OURS_ONLY = setOf(TargetMemberState.DESIRED, TargetMemberState.SYNCED)
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
