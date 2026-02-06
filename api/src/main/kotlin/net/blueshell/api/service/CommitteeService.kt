package net.blueshell.api.service

import net.blueshell.api.model.committee.Committee
import net.blueshell.api.repository.committee.CommitteeRepository
import net.blueshell.api.service.base.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class CommitteeService @Autowired constructor(
    repository: CommitteeRepository
) : BaseModelService<Committee, Long, CommitteeRepository>(repository) {
    fun findAllByUserId(id: Long): MutableList<Committee> {
        return repository.findAllByUserId(id) as MutableList<Committee>
    }
}
