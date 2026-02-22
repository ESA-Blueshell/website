package net.blueshell.api.domain.committee.application

import net.blueshell.api.domain.committee.application.event.CommitteeMembershipChanged
import net.blueshell.api.domain.committee.application.exception.CommitteeNotFoundException
import net.blueshell.api.domain.committee.command.CommitteeMemberData
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.domain.committee.persistence.repository.CommitteeRepository
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.function.Supplier

@Service
class CommitteeService @Autowired constructor(
    repository: CommitteeRepository,
    private val userService: UserService,
    private val trackedEvents: TrackedEventPublisher
) : BaseModelService<Committee, Long, CommitteeRepository>(repository) {
    @Transactional(readOnly = true)
    override fun findById(id: Long): Committee {
        return repository.findById(id).orElseThrow(Supplier {
            CommitteeNotFoundException(id)
        })
    }

    @Transactional
    fun createWithMembers(
        name: String,
        description: String,
        members: List<CommitteeMemberData>
    ): Committee {
        val committee = Committee(name = name, description = description)
        reconcileMembers(committee, members)
        val saved = super.create(committee)
        publishMembershipChanges(saved.id!!, saved.members.map { it.userId }.toSet())
        return saved
    }

    @Transactional
    fun updateWithMembers(
        id: Long,
        name: String,
        description: String,
        members: List<CommitteeMemberData>,
        version: Long?
    ): Committee {
        val committee = findById(id)
        val previousMembers = committee.members.associate { it.userId to it.role }

        committee.name = name
        committee.description = description
        reconcileMembers(committee, members)
        version?.let { committee.version = it }

        val saved = super.update(committee)
        val currentMembers = saved.members.associate { it.userId to it.role }
        val changedUserIds = changedUserIds(previousMembers, currentMembers)
        publishMembershipChanges(saved.id!!, changedUserIds)
        return saved
    }

    fun findAllByUserId(id: Long): MutableList<Committee> {
        return repository.findAllByUserId(id) as MutableList<Committee>
    }

    private fun reconcileMembers(committee: Committee, members: List<CommitteeMemberData>) {
        val existingByUserId = committee.members.associateBy { it.userId }
        val mappedMembers = members.map { memberData ->
            val member = existingByUserId[memberData.userId] ?: CommitteeMember(
                committee = committee,
                user = userService.findById(memberData.userId),
            )
            member.role = memberData.role
            member
        }
        committee.replaceMembers(mappedMembers)
    }

    private fun changedUserIds(
        previousMembers: Map<Long, String?>,
        currentMembers: Map<Long, String?>
    ): Set<Long> {
        val removed = previousMembers.keys - currentMembers.keys
        val added = currentMembers.keys - previousMembers.keys
        val roleChanged = previousMembers.keys.intersect(currentMembers.keys)
            .filter { userId -> previousMembers[userId] != currentMembers[userId] }
            .toSet()
        return removed + added + roleChanged
    }

    private fun publishMembershipChanges(committeeId: Long, userIds: Set<Long>) {
        userIds.forEach { userId ->
            trackedEvents.publish { actor ->
                CommitteeMembershipChanged(
                    userId = userId,
                    committeeId = committeeId,
                    actor = actor
                )
            }
        }
    }
}
