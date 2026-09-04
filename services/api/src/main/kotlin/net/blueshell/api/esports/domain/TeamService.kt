package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRepository
import net.blueshell.api.file.api.StoredPictures
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TeamService(
    private val teams: TeamRepository,
    private val games: GameService,
    private val pictures: StoredPictures,
) : BaseModelService<Team, Long, TeamRepository>(teams) {
    /** Every team the association has. The pool is shared, so it is not asked per game. */
    @Transactional(readOnly = true)
    fun pool(): List<Team> = teams.findAllOrderByNameAsc()

    @Transactional(readOnly = true)
    override fun findById(id: Long): Team =
        teams.findById(id).orElseThrow { TeamNotFoundException(id) }

    @Transactional(readOnly = true)
    fun findByName(name: String): Team? = teams.findByNameIgnoreCase(name)

    /**
     * A team is the association's rather than a game's, so it is created without one: the game
     * arrives when it is fielded. Its art arrives then too, for the same reason.
     */
    @Transactional
    fun create(name: String, icon: String? = null): Team =
        teams.save(Team(name = name.trim(), icon = pictures.of(icon, FileType.TEAM_ICON)))

    /**
     * The team as the caller now says it stands: a write says what the team is, not what changed,
     * so naming no picture takes the logo away and abandoning the edit changes nothing.
     *
     * Taking a logo away leaves the stored file: files are addressed by content, so the row may
     * be another team's logo too.
     */
    @Transactional
    fun update(id: Long, name: String, icon: String? = null): Team {
        val team = findById(id)
        team.name = name.trim()
        team.icon = pictures.of(icon, FileType.TEAM_ICON)
        return teams.save(team)
    }

    @Transactional
    fun delete(id: Long) = teams.delete(findById(id))
}
