package net.blueshell.api.domain.membership.application

import net.blueshell.api.domain.membership.application.event.MembershipChange
import net.blueshell.api.domain.membership.application.event.MembershipChanged
import net.blueshell.api.domain.membership.application.query.MembershipQuery
import net.blueshell.api.domain.membership.persistence.Membership
import net.blueshell.api.domain.membership.persistence.repository.MemberRepository
import net.blueshell.api.domain.membership.persistence.spec.MembershipSpecifications
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
                saved.endDate == null,
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
                saved.endDate == null,
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
                active = false,
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
                active = false,
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
}
