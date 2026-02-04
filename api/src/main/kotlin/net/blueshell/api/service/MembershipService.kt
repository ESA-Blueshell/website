package net.blueshell.api.service

import net.blueshell.api.base.BaseModelService
import net.blueshell.api.controller.filter.MembershipFilter
import net.blueshell.api.model.Membership
import net.blueshell.api.repository.MemberRepository
import net.blueshell.api.repository.spec.MembershipSpecifications
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class MembershipService @Autowired constructor(repository: MemberRepository, events: ApplicationEventPublisher) :
    BaseModelService<Membership, Long, MemberRepository>(repository) {
    fun existsByUserId(userId: Long): Boolean {
        return repository!!.existsByUserId(userId)
    }

    fun findByFilter(filter: MembershipFilter): MutableList<Membership> {
        val spec = MembershipSpecifications.fromFilter(filter, principal)
        return repository.findAll(spec)
    }
}
