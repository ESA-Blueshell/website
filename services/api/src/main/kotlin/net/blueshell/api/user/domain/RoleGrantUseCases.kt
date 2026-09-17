package net.blueshell.api.user.domain

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.RoleChange
import net.blueshell.api.user.persistence.RoleChangeRepository
import net.blueshell.api.user.persistence.User
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * What a person may reach, as an admin decides it.
 *
 * The write states the full set of granted roles rather than a delta, so two admins editing the
 * same person cannot silently undo each other and a repeated request changes nothing.
 */
@Service
class RoleGrantUseCases(
    private val users: UserService,
    private val roleChanges: RoleChangeRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val jobs: JobQueue,
) {
    /** The roles a person holds, split by where each one comes from. */
    @Transactional(readOnly = true)
    fun readRoles(userId: Long): RoleStanding = standingOf(users.findById(userId))

    @Transactional(readOnly = true)
    fun readHistory(userId: Long): List<RoleChange> {
        users.findById(userId)
        return roleChanges.findBySubjectNewestFirst(userId)
    }

    /**
     * Sets the granted roles of [userId] to [granted], leaving the derived ones alone.
     *
     * Records the change and, when it adds or removes admin or board, tells the person by email
     * once the transaction has committed.
     */
    @Transactional
    fun setGrantedRoles(userId: Long, granted: Set<Role>, note: String?): RoleStanding {
        granted.firstOrNull { !GrantedRoles.isAssignable(it) }?.let { throw RoleNotAssignable(it) }

        val subject = users.findById(userId)
        val before = subject.roles.toSet()
        val after = before.filterNot { GrantedRoles.isAssignable(it) }.toSet() + granted
        val actor = currentActor()

        if (actor.id == userId && (after - before).isNotEmpty()) {
            throw AccessDeniedException("Cannot elevate own privileges")
        }
        if (Role.ADMIN in before && Role.ADMIN !in after && roleChanges.countAdministrators() <= 1) {
            throw LastAdministrator()
        }

        if (after == before) return standingOf(subject)

        subject.roles = after.toMutableSet()
        val saved = users.update(subject)

        val record = roleChanges.save(
            RoleChange(
                subject = saved,
                actor = actor,
                rolesBefore = before,
                rolesAfter = after,
                note = note?.takeIf { it.isNotBlank() },
                changedAt = Instant.now(),
            ),
        )
        if (NOTIFIED_ROLES.any { (it in before) != (it in after) }) {
            jobs.runAsync(EmailJobs.RoleChange, EmailJobs.RoleChangePayload(requireNotNull(record.id)))
        }
        return standingOf(saved)
    }

    private fun currentActor(): User {
        val current = currentUserProvider.currentUser()
            ?: throw AccessDeniedException("Roles are changed by a signed-in admin")
        return users.findById(current.id)
    }

    private fun standingOf(user: User): RoleStanding {
        val held = user.roles.toSet()
        return RoleStanding(
            userId = requireNotNull(user.id),
            roles = held,
            granted = held.filter { GrantedRoles.isAssignable(it) }.toSet(),
            derived = held.filter { it in GrantedRoles.DERIVED || it == GrantedRoles.DEFAULT }
                .associateWith { GrantedRoles.sourceOf(it) },
            // Held by inheritance rather than by a row of its own: a consequence of a grant,
            // not a grant. The floor everybody stands on says nothing, so it is left out.
            implied = user.inheritedRoles - held - Role.ANONYMOUS,
            assignable = GrantedRoles.ASSIGNABLE.toSet(),
        )
    }

    companion object {
        /** The two roles worth an email. The rest change quietly. */
        private val NOTIFIED_ROLES = setOf(Role.ADMIN, Role.BOARD)
    }
}

/** What one person may reach, and where each part of it comes from. */
data class RoleStanding(
    val userId: Long,
    val roles: Set<Role>,
    val granted: Set<Role>,
    val derived: Map<Role, RoleSource>,
    val implied: Set<Role>,
    val assignable: Set<Role>,
)
