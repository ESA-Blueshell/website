package net.blueshell.api.domain.user.application

import net.blueshell.api.domain.user.application.event.MembershipChange
import net.blueshell.api.domain.user.application.event.MembershipChanged
import net.blueshell.api.domain.user.application.exception.MembershipNotFoundException
import net.blueshell.api.domain.user.application.query.MembershipQuery
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.repository.MemberRepository
import net.blueshell.api.domain.user.persistence.spec.MembershipSpecifications
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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

    fun findByQuery(query: MembershipQuery): MutableList<Membership> {
        val spec = MembershipSpecifications.fromQuery(
            query,
            currentUserProvider.currentUser()
        )
        return repository.findAll(spec)
    }

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
