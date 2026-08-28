package net.blueshell.api.esports.domain

/**
 * How narrowly a banner was set.
 *
 * A banner always names a game, and may narrow that to a season, a team, or both. The two
 * nullable ids are what the resolution reads; what the banner points at is the caller's.
 */
interface BannerLevel {
    val seasonId: Long?
    val teamId: Long?
}

/**
 * The banner to show for one game, out of everything set for it.
 *
 * A banner applies when nothing it names contradicts what is being shown: one set for a
 * season carries every team in that season, one set for a team carries every season that team
 * played. Of those that apply, the most narrowly set wins — team and season, then team, then
 * season, then the game alone — so one upload restyles a whole game while a single team can
 * still override it.
 *
 * Applicability is checked before specificity rather than counted with it. A banner set for a
 * team in one season names that team, and ranking by how much a banner names would carry it
 * into every other season the team played.
 *
 * Returns null when nothing applies, which the caller answers with the site default.
 */
fun <T : BannerLevel> mostSpecificBanner(candidates: List<T>, seasonId: Long?, teamId: Long?): T? =
    candidates
        .filter { (it.seasonId == null || it.seasonId == seasonId) && (it.teamId == null || it.teamId == teamId) }
        .maxByOrNull { (if (it.teamId != null) 2 else 0) + (if (it.seasonId != null) 1 else 0) }
