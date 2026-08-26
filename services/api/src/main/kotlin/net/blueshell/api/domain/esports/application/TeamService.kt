package net.blueshell.api.domain.esports.application

import net.blueshell.api.domain.esports.application.exception.TeamNotFoundException
import net.blueshell.api.domain.esports.persistence.Team
import net.blueshell.api.domain.esports.persistence.repository.TeamRepository
import net.blueshell.api.shared.enums.Game
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TeamService(
    private val teams: TeamRepository,
) : BaseModelService<Team, Long, TeamRepository>(teams) {
    @Transactional(readOnly = true)
    fun findAllByGame(game: Game): List<Team> = teams.findAllByGameOrderByNameAsc(game)

    @Transactional(readOnly = true)
    override fun findById(id: Long): Team =
        teams.findById(id).orElseThrow { TeamNotFoundException(id) }

    @Transactional(readOnly = true)
    fun findByName(game: Game, name: String): Team? = teams.findByGameAndNameIgnoreCase(game, name)

    @Transactional
    fun create(game: Game, name: String, image: String?): Team =
        teams.save(Team(game = game, name = name.trim(), image = image?.trim()?.ifBlank { null }))

    @Transactional
    fun update(id: Long, name: String, image: String?): Team {
        val team = findById(id)
        team.name = name.trim()
        team.image = image?.trim()?.ifBlank { null }
        return teams.save(team)
    }

    @Transactional
    fun delete(id: Long) = teams.delete(findById(id))
}
