package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.EsportsBanner
import net.blueshell.api.esports.persistence.EsportsBannerRepository
import net.blueshell.api.esports.persistence.Team
import net.blueshell.api.esports.persistence.TeamRepository
import net.blueshell.api.esports.persistence.TeamRosterEntry
import net.blueshell.api.esports.persistence.TeamRosterEntryRepository
import net.blueshell.api.file.api.FileService
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.Game
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

/**
 * The images an admin puts on the esports pages: a team's poster, a player's icon, and the
 * banner behind a page.
 *
 * Removing an image clears the reference and leaves the stored file alone. Files are stored by
 * content hash, so one row can be the poster of two teams that uploaded the same picture, and
 * deleting it on behalf of one of them would take the other's away with it.
 */
@Service
class EsportsMediaService(
    private val files: FileService,
    private val teams: TeamRepository,
    private val entries: TeamRosterEntryRepository,
    private val banners: EsportsBannerRepository,
    private val seasons: SeasonService,
) {
    @Transactional
    fun setTeamPoster(teamId: Long, upload: MultipartFile): Team {
        val team = team(teamId)
        team.poster = files.storeMultipart(upload, FileType.TEAM_POSTER)
        return teams.save(team)
    }

    @Transactional
    fun clearTeamPoster(teamId: Long): Team {
        val team = team(teamId)
        team.poster = null
        return teams.save(team)
    }

    @Transactional
    fun setRosterIcon(entryId: Long, upload: MultipartFile): TeamRosterEntry {
        val entry = entry(entryId)
        entry.icon = files.storeMultipart(upload, FileType.ROSTER_ICON)
        return entries.save(entry)
    }

    @Transactional
    fun clearRosterIcon(entryId: Long): TeamRosterEntry {
        val entry = entry(entryId)
        entry.icon = null
        return entries.save(entry)
    }

    @Transactional(readOnly = true)
    fun findBanners(game: Game): List<EsportsBanner> = banners.findAllByGame(game)

    /**
     * Sets the banner for one combination of game, season and team, replacing whatever was
     * there.
     *
     * An upload against a combination that already has a banner repoints it rather than adding
     * a second: a combination has one banner, and the database says so too.
     */
    @Transactional
    fun setBanner(game: Game, seasonId: Long?, teamId: Long?, upload: MultipartFile): EsportsBanner {
        val team = teamId?.let { team(it) }
        if (team != null && team.game != game) throw BannerTeamPlaysAnotherGameException(team.name, game)
        val season = seasonId?.let { seasons.findById(it) }
        val file = files.storeMultipart(upload, FileType.ESPORTS_BANNER)

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

    private fun entry(id: Long): TeamRosterEntry =
        entries.findById(id).orElseThrow { RosterEntryNotFoundException(id) }
}
