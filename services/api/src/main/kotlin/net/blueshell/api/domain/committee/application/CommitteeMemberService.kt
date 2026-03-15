package net.blueshell.api.domain.committee.application

import net.blueshell.api.domain.committee.application.event.CommitteeMembershipChanged
import net.blueshell.api.domain.committee.application.exception.CommitteeMemberNotFoundException
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.domain.committee.persistence.repository.CommitteeMemberRepository
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.function.Supplier

@Service
class CommitteeMemberService(
    repository: CommitteeMemberRepository,
    private val trackedEvents: TrackedEventPublisher
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
        trackedEvents.publish { actor ->
            CommitteeMembershipChanged(
                userId,
                committeeId,
                actor = actor
            )
        }
    }

    @Transactional
    override fun deleteById(id: CommitteeMember.Id) {
        val member = findById(id)
        super.deleteById(id)
        publishChange(member)
    }

    /**
     * Count the number of committee memberships for a user.
     * Used by other domains to check if a user has committee role.
     */
    @Transactional(readOnly = true)
    fun countMembershipsForUser(userId: Long): Long {
        return repository.countByUser_Id(userId)
    }

    private fun publishChange(member: CommitteeMember) {
        trackedEvents.publish { actor ->
            CommitteeMembershipChanged(
                member.userId,
                member.committeeId,
                actor = actor
            )
        }
    }
}
