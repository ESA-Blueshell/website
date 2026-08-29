package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.EsportsBanner
import net.blueshell.api.esports.persistence.EsportsBannerRepository
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRepository
import net.blueshell.api.shared.enums.FileType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * The banners behind the esports pages, and how narrowly each of them is set.
 *
 * Removing one clears the reference and leaves the stored file alone. Files are addressed by
 * content, so one row can be the banner of two pages that were given the same picture, and
 * deleting it on behalf of one of them would take the other's away with it.
 *
 * A team's poster and a person's line-up icon are not here. They are fields of the team and of
 * the roster entry, and they are written by the save that writes the rest of those records.
 */
@Service
class EsportsMediaService(
    private val pictures: EsportsPictures,
    private val teams: TeamRepository,
    private val banners: EsportsBannerRepository,
    private val seasons: SeasonService,
) {
    @Transactional(readOnly = true)
    fun findBanners(game: String): List<EsportsBanner> = banners.findAllByGame(game)

    /**
     * Sets the banner for one combination of game, season and team, replacing whatever was
     * there.
     *
     * Setting one against a combination that already has a banner repoints it rather than
     * adding a second: a combination has one banner, and the database says so too.
     */
    @Transactional
    fun setBanner(game: String, seasonId: Long?, teamId: Long?, picture: String): EsportsBanner {
        val team = teamId?.let { team(it) }
        if (team != null && team.game != game) throw BannerTeamPlaysAnotherGameException(team.name, game)
        val season = seasonId?.let { seasons.findById(it) }
        val file = pictures.of(picture, FileType.ESPORTS_BANNER) ?: throw PictureNotStoredException()

        val existing = banners.findAllByGame(game)
            .firstOrNull { it.seasonId == seasonId && it.teamId == teamId }
        if (existing != null) {
            existing.file = file
            return banners.save(existing)
        }
        return banners.save(EsportsBanner(game = game, file = file, season = season, team = team))
    }

    @Transactional
    fun removeBanner(id: Long) =
        banners.delete(banners.findById(id).orElseThrow { BannerNotFoundException(id) })

    private fun team(id: Long): Team = teams.findById(id).orElseThrow { TeamNotFoundException(id) }
}
