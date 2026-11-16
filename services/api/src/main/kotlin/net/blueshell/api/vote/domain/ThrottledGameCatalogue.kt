package net.blueshell.api.vote.domain

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Keeps every member's lookups together under IGDB's four requests a second, which is per client
 * rather than per member, and answers a search asked again within [remember] from memory. The api
 * runs as one process (Flagger scales it between none and one), so a limit held here is the whole
 * site's. A lookup that cannot get a turn within [patience] is refused as unavailable.
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

    private val sent = ArrayDeque<Instant>()
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

    // Waits for a free slot in the last second, then runs [lookup]; refused where the wait would use up [patience].
    private fun <T> turn(lookup: () -> T): T {
        val giveUp = clock.instant().plus(patience)
        while (true) {
            val wait =
                synchronized(sent) {
                    val now = clock.instant()
                    while (sent.isNotEmpty() && !sent.first().isAfter(now.minusSeconds(1))) sent.removeFirst()
                    if (sent.size < perSecond) {
                        sent.addLast(now)
                        null
                    } else {
                        Duration.between(now, sent.first().plusSeconds(1))
                    }
                } ?: return lookup()
            if (!clock.instant().plus(wait).isBefore(giveUp)) throw GameSearchUnavailable()
            pause(wait)
        }
    }

    private companion object {
        const val IGDB_REQUESTS_PER_SECOND = 4
        const val REMEMBER_MINUTES = 10L
        const val REMEMBERED = 500
        const val LOAD_FACTOR = 0.75f
    }
}
