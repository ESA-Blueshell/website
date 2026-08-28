package net.blueshell.api.esports.domain

import net.blueshell.api.esports.persistence.EsportsBannerRepository
import net.blueshell.api.file.persistence.File
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Which image a page ends up behind.
 *
 * One query per game rather than one per level: a game holds a handful of banners and which
 * of them wins is decided by comparing them against each other, not by asking four times.
 *
 * Answering with null is an answer: nothing has been set that applies, and the page falls back
 * to the image the frontend bundles.
 */
@Service
class BannerResolutionService(
    private val banners: EsportsBannerRepository,
) {
    @Transactional(readOnly = true)
    fun resolve(game: String, seasonId: Long? = null, teamId: Long? = null): File? =
        mostSpecificBanner(banners.findAllByGame(game), seasonId, teamId)?.file

    /**
     * The banner for each of several teams in one season, read in one go.
     *
     * A page draws every team it shows at once, and resolving them one at a time would be a
     * query per team for a set of banners that does not change between them.
     */
    @Transactional(readOnly = true)
    fun resolveForTeams(game: String, seasonId: Long?, teamIds: Collection<Long>): Map<Long, File> {
        val candidates = banners.findAllByGame(game)
        return teamIds.mapNotNull { teamId ->
            mostSpecificBanner(candidates, seasonId, teamId)?.let { teamId to it.file }
        }.toMap()
    }
}
