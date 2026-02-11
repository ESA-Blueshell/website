package net.blueshell.api.domain.membership.application

import net.blueshell.api.domain.membership.application.event.MembershipChange
import net.blueshell.api.domain.membership.application.event.MembershipChanged
import net.blueshell.api.domain.membership.persistence.repository.MemberRepository
import net.blueshell.api.domain.membership.persistence.Membership
import net.blueshell.api.domain.membership.persistence.filter.MembershipFilter
import net.blueshell.api.domain.membership.persistence.spec.MembershipSpecifications
import net.blueshell.api.shared.event.AfterCommitEventPublisher
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MembershipService @Autowired constructor(
    repository: MemberRepository,
    private val events: AfterCommitEventPublisher
) : BaseModelService<Membership, Long, MemberRepository>(repository) {
    @Transactional
    override fun create(entity: Membership): Membership {
        val saved = super.create(entity)
        events.publish(
            MembershipChanged(
                saved.userId,
                saved.endDate == null,
                MembershipChange.CREATED
            )
        )
        return saved
    }

    @Transactional
    override fun update(entity: Membership): Membership {
        val saved = super.update(entity)
        events.publish(
            MembershipChanged(
                saved.userId,
                saved.endDate == null,
                MembershipChange.UPDATED
            )
        )
        return saved
    }

    @Transactional
    override fun delete(entity: Membership) {
        val userId = entity.userId
        super.delete(entity)
        events.publish(
            MembershipChanged(
                userId,
                active = false,
                changeType = MembershipChange.DELETED
            )
        )
    }

    @Transactional
    override fun deleteById(id: Long) {
        val membership = BaseModelService.findById(id)
        super.deleteById(id)
        events.publish(
            MembershipChanged(
                membership.userId,
                active = false,
                changeType = MembershipChange.DELETED
            )
        )
    }

    fun existsByUserId(userId: Long): Boolean {
        return repository.existsByUserId(userId)
    }

    fun findByFilter(filter: MembershipFilter): MutableList<Membership> {
        val spec = MembershipSpecifications.fromFilter(filter,
            net.blueshell.api.auth.security.IdentityProvider.principal
        )
        return repository.findAll(spec)
    }
}
