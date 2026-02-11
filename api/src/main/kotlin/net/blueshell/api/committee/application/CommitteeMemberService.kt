package net.blueshell.api.committee.application

import net.blueshell.api.committee.application.event.CommitteeMembershipChanged
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.committee.persistence.CommitteeMember
import net.blueshell.api.committee.persistence.repository.CommitteeMemberRepository
import net.blueshell.api.shared.event.AfterCommitEventPublisher
import net.blueshell.api.shared.model.asRef
import net.blueshell.api.shared.service.BaseModelService
import net.blueshell.api.user.persistence.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CommitteeMemberService(
    repository: CommitteeMemberRepository,
    private val events: AfterCommitEventPublisher
) : BaseModelService<CommitteeMember, CommitteeMember.Id, CommitteeMemberRepository>(repository) {
    @Transactional
    override fun create(entity: CommitteeMember): CommitteeMember {
        mergeRefs(entity)
        val saved = super.create(entity)
        publishChange(saved)
        return saved
    }

    @Transactional
    override fun update(entity: CommitteeMember): CommitteeMember {
        mergeRefs(entity)
        val saved = super.update(entity)
        publishChange(saved)
        return saved
    }

    @Transactional
    override fun delete(entity: CommitteeMember) {
        val userId = entity.userId
        val committeeId = entity.committeeId
        super.delete(entity)
        events.publish(CommitteeMembershipChanged(userId, committeeId))
    }

    @Transactional
    override fun deleteById(id: CommitteeMember.Id) {
        val member = findById(id)
        super.deleteById(id)
        publishChange(member)
    }

    private fun publishChange(member: CommitteeMember) {
        events.publish(CommitteeMembershipChanged(member.userId, member.committeeId))
    }

    private fun mergeRefs(member: CommitteeMember) {
        if (member.userId != 0L) {
            member.user = User::class.asRef(member.userId)
        }
        if (member.committeeId != 0L) {
            member.committee = Committee::class.asRef(member.committeeId)
        }
    }
}
