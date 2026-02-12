package net.blueshell.api.domain.committee.application.command

import net.blueshell.api.domain.committee.application.CommitteeService
import net.blueshell.api.domain.committee.command.CreateCommitteeCommand
import net.blueshell.api.domain.committee.command.DeleteCommitteeByIdCommand
import net.blueshell.api.domain.committee.command.FindCommitteeByIdCommand
import net.blueshell.api.domain.committee.command.FindCommitteesCommand
import net.blueshell.api.domain.committee.command.FindCommitteesForCurrentUserCommand
import net.blueshell.api.domain.committee.command.UpdateCommitteeCommand
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.committee.web.mapping.asEntity
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
        val committee = command.dto.asEntity()
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
        committee = command.dto.asEntity(committee)
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
