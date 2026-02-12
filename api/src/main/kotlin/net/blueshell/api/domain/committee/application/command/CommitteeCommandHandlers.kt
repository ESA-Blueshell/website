package net.blueshell.api.domain.committee.application.command

import net.blueshell.api.domain.committee.application.CommitteeService
import net.blueshell.api.domain.committee.command.*
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class FindCommitteesForCurrentUserHandler(
    private val service: CommitteeService
) : CommandHandler<FindCommitteesForCurrentUserCommand, MutableList<Committee>> {
    override val commandType = FindCommitteesForCurrentUserCommand::class

    override fun handle(command: FindCommitteesForCurrentUserCommand): MutableList<Committee> {
        val principalId = command.principalId ?: return mutableListOf()
        return if (command.includeAll) {
            service.findAll()
        } else {
            service.findAllByUserId(principalId)
        }
    }
}

@Component
class FindCommitteesHandler(
    private val service: CommitteeService
) : CommandHandler<FindCommitteesCommand, MutableList<Committee>> {
    override val commandType = FindCommitteesCommand::class

    override fun handle(command: FindCommitteesCommand): MutableList<Committee> {
        return service.findAll()
    }
}

@Component
class FindCommitteeByIdHandler(
    private val service: CommitteeService
) : CommandHandler<FindCommitteeByIdCommand, Committee> {
    override val commandType = FindCommitteeByIdCommand::class

    override fun handle(command: FindCommitteeByIdCommand): Committee {
        return service.findById(command.committeeId)
    }
}

@Component
class CreateCommitteeHandler(
    private val service: CommitteeService
) : CommandHandler<CreateCommitteeCommand, Committee> {
    override val commandType = CreateCommitteeCommand::class

    override fun handle(command: CreateCommitteeCommand): Committee {
        val committee = Committee()
        committee.name = command.name
        committee.description = command.description
        committee.replaceMembers(mapMembers(command.members))
        return service.create(committee)
    }
}

@Component
class UpdateCommitteeHandler(
    private val service: CommitteeService
) : CommandHandler<UpdateCommitteeCommand, Committee> {
    override val commandType = UpdateCommitteeCommand::class

    override fun handle(command: UpdateCommitteeCommand): Committee {
        var committee = service.findById(command.id)
        committee.name = command.name
        committee.description = command.description
        committee.replaceMembers(mapMembers(command.members, committee))
        command.version?.let { committee.version = it }
        return service.update(committee)
    }
}

@Component
class DeleteCommitteeByIdHandler(
    private val service: CommitteeService
) : CommandHandler<DeleteCommitteeByIdCommand, Unit> {
    override val commandType = DeleteCommitteeByIdCommand::class

    override fun handle(command: DeleteCommitteeByIdCommand) {
        service.deleteById(command.id)
    }
}

private fun mapMembers(
    members: MutableList<CommitteeMemberData>,
    committee: Committee? = null
): MutableList<CommitteeMember> {
    val existingByUserId = committee?.members?.associateBy { it.userId } ?: emptyMap()
    return members.map { memberData ->
        val member = existingByUserId[memberData.userId] ?: CommitteeMember()
        member.id.userId = memberData.userId
        member.role = memberData.role
        member
    }.toMutableList()
}
