package net.blueshell.api.feature.committee.service

import net.blueshell.api.feature.committee.model.Committee
import net.blueshell.api.feature.committee.repository.CommitteeRepository
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
