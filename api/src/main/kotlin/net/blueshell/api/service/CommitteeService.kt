package net.blueshell.api.service

import net.blueshell.api.base.BaseModelService
import net.blueshell.api.model.committee.Committee
import net.blueshell.api.repository.committee.CommitteeRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class CommitteeService @Autowired constructor(repository: CommitteeRepository, events: ApplicationEventPublisher) :
    BaseModelService<Committee, Long, CommitteeRepository>(repository) {
    fun findAllByUserId(id: Long): MutableList<Committee> {
        return repository.findAllBy_membersUserIdEquals(id)
    }
}
