package net.blueshell.api.vote.domain

import net.blueshell.api.security.InMemoryRequestRateLimiter
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Keeps every member's lookups together under IGDB's four requests a second, which is per client
 * rather than per member, and answers a search asked again within [remember] from memory. The limit
 * is held per process: while a rollout runs two api pods, together they may reach twice that. A
 * lookup that cannot get a turn within [patience] is refused as unavailable.
 */
class ThrottledGameCatalogue(
    private val catalogue: GameCatalogue,
    private val clock: Clock,
    private val perSecond: Int = IGDB_REQUESTS_PER_SECOND,
    private val remember: Duration = Duration.ofMinutes(REMEMBER_MINUTES),
    private val patience: Duration = Duration.ofSeconds(2),
    private val pause: (Duration) -> Unit = { Thread.sleep(it) },
) : GameCatalogue {
    private data class Asked(
        val query: String,
        val limit: Int,
        val releasedIn: Int?,
    )

    private class Answer(
        val games: List<CatalogueGame>,
        val until: Instant,
    )

    private val limiter = InMemoryRequestRateLimiter(maxBuckets = 1, now = clock::millis)
    private val answers =
        object : LinkedHashMap<Asked, Answer>(REMEMBERED, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Asked, Answer>) = size > REMEMBERED
        }

    override fun search(
        query: String,
        limit: Int,
        releasedIn: Int?,
    ): List<CatalogueGame> {
        val asked = Asked(query.lowercase(), limit, releasedIn)
        synchronized(answers) { answers[asked]?.takeIf { clock.instant().isBefore(it.until) }?.let { return it.games } }
        val games = turn { catalogue.search(query, limit, releasedIn) }
        synchronized(answers) { answers[asked] = Answer(games, clock.instant().plus(remember)) }
        return games
    }

    override fun byId(igdbId: Long): CatalogueGame? = turn { catalogue.byId(igdbId) }

    // An address only: the picture is fetched from the image host, which the API's limit does not cover.
    override fun coverUrl(
        imageId: String,
        size: CoverSize,
    ): String? = catalogue.coverUrl(imageId, size)

    private fun <T> turn(lookup: () -> T): T {
        if (!limiter.awaitTurn(KEY, perSecond, Duration.ofSeconds(1), patience, pause)) throw GameSearchUnavailable()
        return lookup()
    }

    private companion object {
        const val KEY = "igdb"
        const val IGDB_REQUESTS_PER_SECOND = 4
        const val REMEMBER_MINUTES = 10L
        const val REMEMBERED = 500
        const val LOAD_FACTOR = 0.75f
    }
}
