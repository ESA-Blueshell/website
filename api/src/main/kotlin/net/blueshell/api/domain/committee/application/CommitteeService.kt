package net.blueshell.api.domain.committee.application

import net.blueshell.api.domain.committee.application.exception.CommitteeNotFoundException
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.committee.persistence.repository.CommitteeRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.function.Supplier

@Service
class CommitteeService @Autowired constructor(
    repository: CommitteeRepository
) : BaseModelService<Committee, Long, CommitteeRepository>(repository) {
    @Transactional(readOnly = true)
    override fun findById(id: Long): Committee {
        return repository.findById(id).orElseThrow(Supplier {
            CommitteeNotFoundException(id)
        })
    }

    fun findAllByUserId(id: Long): MutableList<Committee> {
        return repository.findAllByUserId(id) as MutableList<Committee>
    }
}
