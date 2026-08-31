package net.blueshell.api.user.api

import net.blueshell.api.user.persistence.Membership
import net.blueshell.api.user.persistence.MemberRepository
import net.blueshell.api.user.persistence.MembershipSpecifications
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import net.blueshell.api.user.domain.MembershipChange
import net.blueshell.api.user.domain.MembershipNotFoundException
import net.blueshell.api.user.domain.MembershipQuery

@Service
class MembershipService @Autowired constructor(
    repository: MemberRepository,
    private val trackedEvents: TrackedEventPublisher,
    private val currentUserProvider: CurrentUserProvider
) : BaseModelService<Membership, Long, MemberRepository>(repository) {
    @Transactional
    override fun create(entity: Membership): Membership {
        val saved = super.create(entity)
        trackedEvents.publish { actor ->
            MembershipChanged(
                saved.userId,
                repository.existsByUser_IdAndEndDateIsNull(saved.userId),
                MembershipChange.CREATED,
                actor = actor
            )
        }
        return saved
    }

    @Transactional
    override fun update(entity: Membership): Membership {
        val saved = super.update(entity)
        trackedEvents.publish { actor ->
            MembershipChanged(
                saved.userId,
                repository.existsByUser_IdAndEndDateIsNull(saved.userId),
                MembershipChange.UPDATED,
                actor = actor
            )
        }
        return saved
    }

    @Transactional
    override fun delete(entity: Membership) {
        val userId = entity.userId
        super.delete(entity)
        trackedEvents.publish { actor ->
            MembershipChanged(
                userId,
                repository.existsByUser_IdAndEndDateIsNull(userId),
                changeType = MembershipChange.DELETED,
                actor = actor
            )
        }
    }

    @Transactional
    override fun deleteById(id: Long) {
        val membership = findById(id)
        super.deleteById(id)
        trackedEvents.publish { actor ->
            MembershipChanged(
                membership.userId,
                repository.existsByUser_IdAndEndDateIsNull(membership.userId),
                changeType = MembershipChange.DELETED,
                actor = actor
            )
        }
    }

    fun existsByUserId(userId: Long): Boolean {
        return repository.existsByUser_Id(userId)
    }

    fun existsActiveMembershipByUserId(userId: Long): Boolean {
        return repository.existsByUser_IdAndEndDateIsNull(userId)
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
            MembershipChanged(userId, repository.existsByUser_IdAndEndDateIsNull(userId), MembershipChange.UPDATED, actor = actor)
        }
        return findById(id)
    }
}
