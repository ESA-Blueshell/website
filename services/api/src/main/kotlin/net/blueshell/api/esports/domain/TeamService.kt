package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRepository
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TeamService(
    private val teams: TeamRepository,
    private val games: GameService,
    private val pictures: EsportsPictures,
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
     * The team as the dialog that edits it now says it stands: its name and its logo, which is
     * everything a team is once the art it is drawn with belongs to the fielding.
     *
     * The logo is part of the save rather than something applied the moment it was chosen, so
     * cancelling that dialog leaves the team exactly as it was. Naming no picture takes the logo
     * away, which is what the picker's Remove does: the write says what the team is, not what
     * changed about it.
     *
     * Taking a logo away leaves the stored file alone. Files are addressed by content, so the
     * row may be another team's logo too, and deleting it on behalf of one of them would take
     * the other's away with it.
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
