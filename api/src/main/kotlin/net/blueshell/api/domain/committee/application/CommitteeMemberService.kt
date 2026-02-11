package net.blueshell.api.domain.committee.application

import net.blueshell.api.domain.committee.application.event.CommitteeMembershipChanged
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.domain.committee.persistence.repository.CommitteeMemberRepository
import net.blueshell.api.shared.event.AfterCommitEventPublisher
import net.blueshell.api.shared.model.asRef
import net.blueshell.api.shared.service.BaseModelService
import net.blueshell.api.domain.user.persistence.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CommitteeMemberService(
    repository: CommitteeMemberRepository,
    private val events: AfterCommitEventPublisher
) : BaseModelService<CommitteeMember, CommitteeMember.Id, CommitteeMemberRepository>(repository) {
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
