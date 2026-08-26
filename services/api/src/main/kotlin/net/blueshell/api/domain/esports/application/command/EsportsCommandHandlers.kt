package net.blueshell.api.domain.esports.application.command

import net.blueshell.api.domain.esports.application.EsportsPageQueryService
import net.blueshell.api.domain.esports.application.SeasonService
import net.blueshell.api.domain.esports.application.TeamRosterService
import net.blueshell.api.domain.esports.application.TeamService
import net.blueshell.api.domain.esports.application.UserGameAccountService
import net.blueshell.api.domain.esports.command.AddRosterEntryCommand
import net.blueshell.api.domain.esports.command.ClearGameAccountCommand
import net.blueshell.api.domain.esports.command.CreateSeasonCommand
import net.blueshell.api.domain.esports.command.CreateTeamCommand
import net.blueshell.api.domain.esports.command.DeleteSeasonCommand
import net.blueshell.api.domain.esports.command.DeleteTeamCommand
import net.blueshell.api.domain.esports.command.EsportsPageView
import net.blueshell.api.domain.esports.command.FindEsportsPageCommand
import net.blueshell.api.domain.esports.command.FindGameAccountsCommand
import net.blueshell.api.domain.esports.command.FindRosterCommand
import net.blueshell.api.domain.esports.command.FindSeasonsCommand
import net.blueshell.api.domain.esports.command.FindTeamsCommand
import net.blueshell.api.domain.esports.command.LinkRosterEntryCommand
import net.blueshell.api.domain.esports.command.RemoveRosterEntryCommand
import net.blueshell.api.domain.esports.command.SetGameAccountCommand
import net.blueshell.api.domain.esports.command.UpdateRosterEntryCommand
import net.blueshell.api.domain.esports.command.UpdateSeasonCommand
import net.blueshell.api.domain.esports.command.UpdateTeamCommand
import net.blueshell.api.domain.esports.persistence.Season
import net.blueshell.api.domain.esports.persistence.Team
import net.blueshell.api.domain.esports.persistence.TeamRosterEntry
import net.blueshell.api.domain.esports.persistence.UserGameAccount
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class FindSeasonsHandler(private val service: SeasonService) :
    CommandHandler<FindSeasonsCommand, List<Season>> {
    override val commandType = FindSeasonsCommand::class

    override fun handle(command: FindSeasonsCommand): List<Season> = service.findAll()
}

@Component
class CreateSeasonHandler(private val service: SeasonService) :
    CommandHandler<CreateSeasonCommand, Season> {
    override val commandType = CreateSeasonCommand::class

    override fun handle(command: CreateSeasonCommand): Season =
        service.create(command.name, command.startDate, command.endDate)
}

@Component
class UpdateSeasonHandler(private val service: SeasonService) :
    CommandHandler<UpdateSeasonCommand, Season> {
    override val commandType = UpdateSeasonCommand::class

    override fun handle(command: UpdateSeasonCommand): Season =
        service.update(command.id, command.name, command.startDate, command.endDate)
}

@Component
class DeleteSeasonHandler(private val service: SeasonService) :
    CommandHandler<DeleteSeasonCommand, Unit> {
    override val commandType = DeleteSeasonCommand::class

    override fun handle(command: DeleteSeasonCommand) = service.delete(command.id)
}

@Component
class FindTeamsHandler(private val service: TeamService) :
    CommandHandler<FindTeamsCommand, List<Team>> {
    override val commandType = FindTeamsCommand::class

    override fun handle(command: FindTeamsCommand): List<Team> = service.findAllByGame(command.game)
}

@Component
class CreateTeamHandler(private val service: TeamService) :
    CommandHandler<CreateTeamCommand, Team> {
    override val commandType = CreateTeamCommand::class

    override fun handle(command: CreateTeamCommand): Team =
        service.create(command.game, command.name, command.image)
}

@Component
class UpdateTeamHandler(private val service: TeamService) :
    CommandHandler<UpdateTeamCommand, Team> {
    override val commandType = UpdateTeamCommand::class

    override fun handle(command: UpdateTeamCommand): Team =
        service.update(command.id, command.name, command.image)
}

@Component
class DeleteTeamHandler(private val service: TeamService) :
    CommandHandler<DeleteTeamCommand, Unit> {
    override val commandType = DeleteTeamCommand::class

    override fun handle(command: DeleteTeamCommand) = service.delete(command.id)
}

@Component
class FindRosterHandler(private val service: TeamRosterService) :
    CommandHandler<FindRosterCommand, List<TeamRosterEntry>> {
    override val commandType = FindRosterCommand::class

    override fun handle(command: FindRosterCommand): List<TeamRosterEntry> =
        service.findByTeamAndSeason(command.teamId, command.seasonId)
}

@Component
class AddRosterEntryHandler(private val service: TeamRosterService) :
    CommandHandler<AddRosterEntryCommand, TeamRosterEntry> {
    override val commandType = AddRosterEntryCommand::class

    override fun handle(command: AddRosterEntryCommand): TeamRosterEntry =
        service.add(
            teamId = command.teamId,
            seasonId = command.seasonId,
            handle = command.handle,
            role = command.role,
            userId = command.userId,
            displayName = command.displayName,
        )
}

@Component
class UpdateRosterEntryHandler(private val service: TeamRosterService) :
    CommandHandler<UpdateRosterEntryCommand, TeamRosterEntry> {
    override val commandType = UpdateRosterEntryCommand::class

    override fun handle(command: UpdateRosterEntryCommand): TeamRosterEntry =
        service.update(command.id, command.handle, command.role, command.displayName, command.sortIndex)
}

@Component
class LinkRosterEntryHandler(private val service: TeamRosterService) :
    CommandHandler<LinkRosterEntryCommand, TeamRosterEntry> {
    override val commandType = LinkRosterEntryCommand::class

    override fun handle(command: LinkRosterEntryCommand): TeamRosterEntry =
        service.link(command.id, command.userId)
}

@Component
class RemoveRosterEntryHandler(private val service: TeamRosterService) :
    CommandHandler<RemoveRosterEntryCommand, Unit> {
    override val commandType = RemoveRosterEntryCommand::class

    override fun handle(command: RemoveRosterEntryCommand) = service.remove(command.id)
}

@Component
class FindGameAccountsHandler(private val service: UserGameAccountService) :
    CommandHandler<FindGameAccountsCommand, List<UserGameAccount>> {
    override val commandType = FindGameAccountsCommand::class

    override fun handle(command: FindGameAccountsCommand): List<UserGameAccount> =
        service.findAllForUser(command.userId)
}

@Component
class SetGameAccountHandler(private val service: UserGameAccountService) :
    CommandHandler<SetGameAccountCommand, UserGameAccount> {
    override val commandType = SetGameAccountCommand::class

    override fun handle(command: SetGameAccountCommand): UserGameAccount =
        service.set(command.userId, command.game, command.handle)
}

@Component
class ClearGameAccountHandler(private val service: UserGameAccountService) :
    CommandHandler<ClearGameAccountCommand, Unit> {
    override val commandType = ClearGameAccountCommand::class

    override fun handle(command: ClearGameAccountCommand) = service.clear(command.userId, command.game)
}

@Component
class FindEsportsPageHandler(private val service: EsportsPageQueryService) :
    CommandHandler<FindEsportsPageCommand, EsportsPageView> {
    override val commandType = FindEsportsPageCommand::class

    override fun handle(command: FindEsportsPageCommand): EsportsPageView =
        service.page(command.game, command.seasonId)
}
