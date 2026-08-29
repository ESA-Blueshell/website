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
    private val games: GamePageService,
    private val pictures: EsportsPictures,
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
    fun create(game: String, name: String, image: String?, poster: String? = null): Team =
        teams.save(
            Team(
                game = games.requireGame(game).game,
                name = name.trim(),
                image = image?.trim()?.ifBlank { null },
                poster = pictures.of(poster, FileType.TEAM_POSTER),
            ),
        )

    /**
     * The team as the dialog that edits it now says it stands.
     *
     * The poster is part of the save rather than something applied the moment it was chosen,
     * so cancelling that dialog leaves the team exactly as it was. Naming no picture takes the
     * poster away, which is what the picker's Remove does: the write says what the team is,
     * not what changed about it.
     *
     * Taking a poster away leaves the stored file alone. Files are addressed by content, so
     * the row may be another team's poster too, and deleting it on behalf of one of them would
     * take the other's away with it.
     */
    @Transactional
    fun update(id: Long, name: String, image: String?, poster: String? = null): Team {
        val team = findById(id)
        team.name = name.trim()
        team.image = image?.trim()?.ifBlank { null }
        team.poster = pictures.of(poster, FileType.TEAM_POSTER)
        return teams.save(team)
    }

    @Transactional
    fun delete(id: Long) = teams.delete(findById(id))
}
