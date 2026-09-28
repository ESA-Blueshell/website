package net.blueshell.api.discord.domain

import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * One answer read from Discord and kept for [freshFor], so a page view is not a Discord call. A
 * read that throws or answers null serves the last answer, or null where there was none, so the
 * site shows what it last knew while Discord is down. [what] names the answer in the log.
 */
internal class KeptRead<T : Any>(
    private val what: String,
    private val freshFor: Duration,
    private val clock: Clock,
) {
    @Volatile private var kept: Pair<Instant, T>? = null

    /** The kept answer while fresh, unless [again] asks Discord regardless. */
    fun get(
        again: Boolean = false,
        read: () -> T?,
    ): T? {
        val now = clock.instant()
        kept?.let { (at, answer) -> if (!again && Duration.between(at, now) < freshFor) return answer }
        return runCatching(read)
            .onFailure { log.warn("$what could not be read", it) }
            .getOrNull()
            ?.also { kept = now to it }
            ?: kept?.second
    }

    private companion object {
        private val log = LoggerFactory.getLogger(KeptRead::class.java)
    }
}
