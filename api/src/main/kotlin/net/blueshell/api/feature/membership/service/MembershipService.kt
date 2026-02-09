package net.blueshell.api.feature.membership.service

import net.blueshell.api.feature.membership.model.Membership
import net.blueshell.api.feature.membership.model.filter.MembershipFilter
import net.blueshell.api.feature.membership.repository.MemberRepository
import net.blueshell.api.feature.membership.repository.spec.MembershipSpecifications
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
