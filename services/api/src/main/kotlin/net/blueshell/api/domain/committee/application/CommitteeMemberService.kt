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

    /**
     * Returns one [CommitteeMembershipWindow] per committee_members row owned
     * by the user, including soft-deleted ones — soft-deletes are the way
     * "ended membership" is encoded today, so callers like the cohort engine
     * need them to compute period-overlap facts. `leftAt` is the soft-delete
     * sentinel for currently-active memberships and the removal timestamp
     * otherwise. The native repo query takes care of bypassing the entity's
     * @SQLRestriction.
     */
    @Transactional(readOnly = true)
    fun findMembershipWindowsForUser(userId: Long): List<CommitteeMembershipWindow> =
        repository.findWindowsByUserId(userId).map { row ->
            CommitteeMembershipWindow(
                committeeId = (row[0] as Number).toLong(),
                joinedAt = (row[1] as java.sql.Timestamp).toInstant(),
                leftAt = (row[2] as java.sql.Timestamp).toInstant(),
            )
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
