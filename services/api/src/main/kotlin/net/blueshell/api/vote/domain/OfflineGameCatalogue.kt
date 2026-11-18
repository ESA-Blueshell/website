package net.blueshell.api.vote.domain

internal val OFFLINE_GAMES =
    listOf(
        CatalogueGame(1942, "The Witcher 3: Wild Hunt", null, 2015),
        CatalogueGame(7346, "The Legend of Zelda: Breath of the Wild", null, 2017),
        CatalogueGame(1020, "Grand Theft Auto V", null, 2013),
        CatalogueGame(119133, "Elden Ring", null, 2022),
        CatalogueGame(26226, "Celeste", null, 2018),
        CatalogueGame(113112, "Hades", null, 2020),
        CatalogueGame(14593, "Hollow Knight", null, 2017),
        CatalogueGame(17000, "Stardew Valley", null, 2016),
        CatalogueGame(72, "Portal 2", null, 2011),
        CatalogueGame(250616, "Hollow Knight: Silksong", null, 2025),
        CatalogueGame(325591, "Dev Game of 2026: Echoes", null, 2026),
        CatalogueGame(325592, "Dev Game of 2026: Tides", null, 2026),
    )

/**
 * A short fixed catalogue for development and tests without IGDB credentials, so a vote can be
 * tried locally. The ids stand in for IGDB's and are not checked against it; there are no covers.
 */
class OfflineGameCatalogue : GameCatalogue {
    override fun search(
        query: String,
        limit: Int,
        releasedIn: Int?,
    ): List<CatalogueGame> =
        OFFLINE_GAMES
            .filter { it.name.contains(query, ignoreCase = true) && (releasedIn == null || it.firstReleaseYear == releasedIn) }
            .take(limit)

    override fun byId(igdbId: Long): CatalogueGame? = OFFLINE_GAMES.firstOrNull { it.igdbId == igdbId }

    // Its games have no covers.
    override fun coverUrl(
        imageId: String,
        size: CoverSize,
    ): String? = null
}
