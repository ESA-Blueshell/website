package net.blueshell.api.user.api

import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.user.persistence.Membership
import net.blueshell.api.user.persistence.MemberRepository
import net.blueshell.api.user.persistence.MembershipSpecifications
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.shared.event.TrackedEventPublisher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import net.blueshell.api.user.domain.MembershipChange
import net.blueshell.api.user.domain.MembershipNotFoundException
import net.blueshell.api.user.domain.MembershipQuery
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

@Service
// The whole membership aggregate's surface, one method past the limit.
@Suppress("TooManyFunctions")
class MembershipService @Autowired constructor(
    private val repository: MemberRepository,
    private val trackedEvents: TrackedEventPublisher,
    private val currentUserProvider: CurrentUserProvider
) {
    @Transactional
    fun create(entity: Membership): Membership {
        // Only an honorary membership owes nothing, so only it starts active (api ADR-036).
        if (entity.memberType == MemberType.HONORARY && entity.activatedOn == null) entity.activatedOn = entity.startDate
        val saved = written(entity)
        trackedEvents.publish { actor ->
            MembershipChanged(
                saved.userId,
                activeHeldBy(saved.userId),
                MembershipChange.CREATED,
                actor = actor
            )
        }
        return saved
    }

    @Transactional
    fun update(entity: Membership): Membership {
        if (entity.memberType == MemberType.HONORARY && entity.activatedOn == null) entity.activatedOn = LocalDate.now()
        val saved = rewritten(entity)
        trackedEvents.publish { actor ->
            MembershipChanged(
                saved.userId,
                activeHeldBy(saved.userId),
                MembershipChange.UPDATED,
                actor = actor
            )
        }
        return saved
    }

    @Transactional
    fun delete(entity: Membership) {
        val userId = entity.userId
        repository.delete(entity)
        trackedEvents.publish { actor ->
            MembershipChanged(
                userId,
                activeHeldBy(userId),
                changeType = MembershipChange.DELETED,
                actor = actor
            )
        }
    }

    @Transactional
    fun deleteById(id: Long) {
        val membership = findById(id)
        repository.delete(membership)
        trackedEvents.publish { actor ->
            MembershipChanged(
                membership.userId,
                activeHeldBy(membership.userId),
                changeType = MembershipChange.DELETED,
                actor = actor
            )
        }
    }

    /**
     * A contribution was paid, so the user's pending membership becomes active and they a member.
     * A membership already active stays as it was: a later period's payment changes nothing.
     */
    @Transactional
    fun activatePending(userId: Long) {
        val pending = repository.findByUser_Id(userId).filter { it.isPending }.ifEmpty { return }
        pending.forEach { it.activatedOn = LocalDate.now() }
        repository.saveAll(pending)
        trackedEvents.publish { actor -> MembershipChanged(userId, true, MembershipChange.UPDATED, actor = actor) }
    }

    fun existsByUserId(userId: Long): Boolean {
        return repository.existsByUser_Id(userId)
    }

    /** Whether the user holds a membership that has not ended, pending or active. */
    fun existsRunningMembershipByUserId(userId: Long): Boolean = repository.existsByUser_IdAndEndDateIsNull(userId)

    fun existsActiveMembershipByUserId(userId: Long): Boolean {
        return activeHeldBy(userId)
    }

    fun findByUserId(userId: Long): MutableList<Membership> {
        return repository.findByUser_Id(userId)
    }

    /** Memberships held by any of these users, grouped per user, in one read. */
    @Transactional(readOnly = true)
    fun findByUserIds(userIds: Collection<Long>): Map<Long, List<Membership>> =
        if (userIds.isEmpty()) emptyMap() else repository.findByUser_IdIn(userIds).groupBy { it.userId }

    fun findByQuery(query: MembershipQuery): MutableList<Membership> {
        val spec = MembershipSpecifications.fromQuery(
            query,
            currentUserProvider.currentUser()
        )
        return repository.findAll(spec)
    }

    /**
     * Everybody whose membership overlapped the window. Mirrors the user manager's
     * "member in period" column, which computes the same rule in the frontend.
     */
    @Transactional(readOnly = true)
    fun findUserIdsOverlapping(from: LocalDate, to: LocalDate): Set<Long> =
        repository.findUserIdsOverlapping(from, to).toSet()

    /** Memberships held by any of these users, grouped per user, with each member loaded. */
    @Transactional(readOnly = true)
    fun findByUserIdsWithMembers(userIds: Collection<Long>): Map<Long, List<Membership>> =
        if (userIds.isEmpty()) emptyMap() else repository.findByUserIdsWithMembers(userIds).groupBy { it.userId }

    /** Every membership that overlapped the window, with its member loaded. */
    @Transactional(readOnly = true)
    fun findOverlappingWithMembers(from: LocalDate, to: LocalDate): List<Membership> =
        repository.findOverlappingWithMembers(from, to)

    @Transactional(readOnly = true)
    fun heldMembershipBetween(userId: Long, from: LocalDate, to: LocalDate): Boolean =
        repository.existsOverlapping(userId, from, to)

    fun findDeletedByUserId(userId: Long): MutableList<Membership> = repository.findDeletedByUser_Id(userId)

    fun findDeletedById(id: Long): Membership? = repository.findDeletedById(id)

    @Transactional
    fun restore(membership: Membership): Membership {
        val id = membership.id ?: throw MembershipNotFoundException(null)
        // Clears deleted_at back to the sentinel; 0 rows means it was already
        // restored (or never deleted) — treat as not-found rather than a silent no-op.
        if (repository.restoreById(id) == 0) throw MembershipNotFoundException(id)
        val userId = membership.userId
        trackedEvents.publish { actor ->
            MembershipChanged(userId, activeHeldBy(userId), MembershipChange.UPDATED, actor = actor)
        }
        return findById(id)
    }

    // Active means running and paid for once; a pending membership carries no member role.
    private fun activeHeldBy(userId: Long): Boolean = repository.existsByUser_IdAndEndDateIsNullAndActivatedOnIsNotNull(userId)

    // Read back after each write, so the columns the database fills are on the answer.
    @PersistenceContext
    private lateinit var em: EntityManager

    private fun written(row: Membership): Membership = repository.saveAndFlush(row).also(em::refresh)

    // The existence query flushes the session first, which writes what the edit cascades (a new
    // address on a user, say) before the merge; merging it unwritten fails on the lazy owner.
    private fun rewritten(row: Membership): Membership {
        val id = row.id
        if (id == null || !repository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found with id: $id")
        }
        return written(row)
    }

    @Transactional(readOnly = true)
    fun findById(id: Long): Membership =
        repository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found with id: $id")
        }
}
