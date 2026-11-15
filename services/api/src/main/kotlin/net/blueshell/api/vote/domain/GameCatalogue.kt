package net.blueshell.api.vote.domain

/** A game as the catalogue describes it. [coverImageId] names an IGDB image, not a stored file. */
data class CatalogueGame(
    val igdbId: Long,
    val name: String,
    val coverImageId: String?,
    val firstReleaseYear: Int?,
)

/** Where games are looked up: IGDB, or a short fixed list in development without credentials. */
interface GameCatalogue {
    /** Games whose name matches [query], best match first, at most [limit]; only those first released in [releasedIn] when given. */
    fun search(
        query: String,
        limit: Int,
        releasedIn: Int?,
    ): List<CatalogueGame>

    /** The game with that IGDB id, or null when there is none. */
    fun byId(igdbId: Long): CatalogueGame?
}

/** A catalogue that refuses every search, for an environment without IGDB credentials. */
object UnavailableGameCatalogue : GameCatalogue {
    override fun search(
        query: String,
        limit: Int,
        releasedIn: Int?,
    ): List<CatalogueGame> = throw GameSearchUnavailable()

    override fun byId(igdbId: Long): CatalogueGame? = throw GameSearchUnavailable()
}
