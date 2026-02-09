package net.blueshell.api.committee.application

import net.blueshell.api.committee.domain.model.Committee
import net.blueshell.api.committee.persistence.CommitteeRepository
import net.blueshell.api.shared.service.BaseModelService
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
