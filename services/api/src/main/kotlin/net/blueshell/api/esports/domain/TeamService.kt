package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TeamService(
    private val teams: TeamRepository,
    private val games: GamePageService,
) : BaseModelService<Team, Long, TeamRepository>(teams) {
    @Transactional(readOnly = true)
    fun findAllByGame(game: String): List<Team> = teams.findAllByGameOrderByNameAsc(game)

    @Transactional(readOnly = true)
    override fun findById(id: Long): Team =
        teams.findById(id).orElseThrow { TeamNotFoundException(id) }

    @Transactional(readOnly = true)
    fun findByName(game: String, name: String): Team? = teams.findByGameAndNameIgnoreCase(game, name)

    /** A team belongs to a game, so a code naming none is refused before anything is written. */
    @Transactional
    fun create(game: String, name: String, image: String?): Team =
        teams.save(Team(game = games.requireGame(game).game, name = name.trim(), image = image?.trim()?.ifBlank { null }))

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
