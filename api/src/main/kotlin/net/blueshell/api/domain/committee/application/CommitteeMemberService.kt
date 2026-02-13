package net.blueshell.api.domain.committee.application

import net.blueshell.api.domain.committee.application.event.CommitteeMembershipChanged
import net.blueshell.api.domain.committee.application.exception.CommitteeMemberNotFoundException
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.domain.committee.persistence.repository.CommitteeMemberRepository
import net.blueshell.api.shared.event.AfterCommitEventPublisher
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.function.Supplier

@Service
class CommitteeMemberService(
    repository: CommitteeMemberRepository,
    private val events: AfterCommitEventPublisher
) : BaseModelService<CommitteeMember, CommitteeMember.Id, CommitteeMemberRepository>(repository) {
    @Transactional(readOnly = true)
    override fun findById(id: CommitteeMember.Id): CommitteeMember {
        return repository.findById(id).orElseThrow(Supplier {
            CommitteeMemberNotFoundException(id.committeeId!!, id.userId!!)
        })
    }
    @Transactional
    override fun create(entity: CommitteeMember): CommitteeMember {
        val saved = super.create(entity)
        publishChange(saved)
        return saved
    }

    @Transactional
    override fun update(entity: CommitteeMember): CommitteeMember {
        val saved = super.update(entity)
        publishChange(saved)
        return saved
    }

    @Transactional
    override fun delete(entity: CommitteeMember) {
        val userId = entity.userId
        val committeeId = entity.committeeId
        super.delete(entity)
        events.publish(
            CommitteeMembershipChanged(
                userId,
                committeeId
            )
        )
    }

    @Transactional
    override fun deleteById(id: CommitteeMember.Id) {
        val member = findById(id)
        super.deleteById(id)
        publishChange(member)
    }

    private fun publishChange(member: CommitteeMember) {
        events.publish(
            CommitteeMembershipChanged(
                member.userId,
                member.committeeId
            )
        )
    }
}
