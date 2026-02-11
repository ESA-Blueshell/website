package net.blueshell.api.committee.application

import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.committee.persistence.repository.CommitteeRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CommitteeService @Autowired constructor(
    repository: CommitteeRepository
) : BaseModelService<Committee, Long, CommitteeRepository>(repository) {
    @Transactional
    override fun create(entity: Committee): Committee {
        mergeAssociations(entity)
        return super.create(entity)
    }

    @Transactional
    override fun update(entity: Committee): Committee {
        mergeAssociations(entity)
        return super.update(entity)
    }

    fun findAllByUserId(id: Long): MutableList<Committee> {
        return repository.findAllByUserId(id) as MutableList<Committee>
    }

    private fun mergeAssociations(committee: Committee) {
        // Set parent references for one-to-many relationship
        committee.members.forEach { member ->
            member.committee = committee
        }
    }
}
