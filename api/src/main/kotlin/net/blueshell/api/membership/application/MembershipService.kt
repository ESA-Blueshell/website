package net.blueshell.api.membership.application

import net.blueshell.api.membership.persistence.Membership
import net.blueshell.api.membership.persistence.filter.MembershipFilter
import net.blueshell.api.membership.persistence.MemberRepository
import net.blueshell.api.membership.persistence.spec.MembershipSpecifications
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class MembershipService @Autowired constructor(repository: MemberRepository, events: ApplicationEventPublisher) :
    BaseModelService<Membership, Long, MemberRepository>(repository) {
    fun existsByUserId(userId: Long): Boolean {
        return repository.existsByUserId(userId)
    }

    fun findByFilter(filter: MembershipFilter): MutableList<Membership> {
        val spec = MembershipSpecifications.fromFilter(filter, principal)
        return repository.findAll(spec)
    }
}
