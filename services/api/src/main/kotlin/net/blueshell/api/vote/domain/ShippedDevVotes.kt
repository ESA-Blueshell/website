package net.blueshell.api.vote.domain

import net.blueshell.api.shared.seed.SeedOrder
import net.blueshell.api.vote.persistence.CandidateTrack
import net.blueshell.api.vote.persistence.CandidateTrackRepository
import net.blueshell.api.vote.persistence.TrackSource
import net.blueshell.api.vote.persistence.Vote
import net.blueshell.api.vote.persistence.VoteCandidate
import net.blueshell.api.vote.persistence.VoteCandidateRepository
import net.blueshell.api.vote.persistence.VoteKind
import net.blueshell.api.vote.persistence.VotePlacing
import net.blueshell.api.vote.persistence.VotePlacingRepository
import net.blueshell.api.vote.persistence.VoteRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId

// A song for each game of the offline catalogue, so a dev vote has tracks to pick and results to show.
private val DEV_SONGS =
    mapOf(
        1942L to "Silver for Monsters",
        7346L to "Main Theme",
        1020L to "Welcome to Los Santos",
        119133L to "Elden Ring",
        26226L to "Resurrections",
        113112L to "Good Riddance",
        14593L to "Greenpath",
        17000L to "Spring (It's a Big World Outside)",
        72L to "Want You Gone",
        250616L to "Silksong",
    )

private const val PAST_YEARS = 3
private const val DEV_TOP = 8
private const val FIRST_POINTS = 60
private const val POINTS_STEP = 6
private const val FIRST_VOTERS = 9

/**
 * Puts a few OST Top 50 votes into a development database on start: the last few Decembers,
 * revealed with a top of the offline catalogue's games, and this year's, open for voting, so the
 * page can be tried at every state and its years moved between without waiting for December.
 *
 * Development only, and only the years that have no vote yet: a year somebody started by hand is
 * left as it stands, and a second start writes nothing.
 */
@Component
@Profile("dev")
class ShippedDevVotes(
    private val votes: VoteRepository,
    private val candidates: VoteCandidateRepository,
    private val tracks: CandidateTrackRepository,
    private val placings: VotePlacingRepository,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {
    @Order(SeedOrder.SIGN_UPS)
    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        val written =
            try {
                transactions.execute { apply() }
            } catch (e: RuntimeException) {
                log.warn("[dev-votes] the dev votes could not be written: {}", e.message)
                return
            }
        if (written > 0) log.info("[dev-votes] {} OST Top 50 votes are now in the database", written)
    }

    /** Writes every missing year's vote and answers how many it wrote. */
    fun apply(): Int {
        val now = clock.instant()
        val thisYear = now.atZone(AMSTERDAM).year
        val songs = DEV_SONGS.mapNotNull { (igdbId, title) -> songOf(igdbId, title) }
        var written = 0
        for (year in thisYear - PAST_YEARS until thisYear) {
            if (votes.findByKindAndYear(VoteKind.OST_TOP_50, year) != null) continue
            val vote = votes.saveAndFlush(december(year).apply { ballotCount = FIRST_VOTERS * 2 + year % VOTER_SPREAD })
            vote.revealedAt = vote.revealAt
            // Each year leads with another game, so the years read differently.
            val order = songs.indices.map { songs[(it + year) % songs.size] }.take(DEV_TOP)
            placings.saveAll(
                order.mapIndexed { index, track ->
                    VotePlacing(vote.id!!, index + 1, track.candidate, track, FIRST_POINTS - index * POINTS_STEP, FIRST_VOTERS - index)
                },
            )
            written++
        }
        if (votes.findByKindAndYear(VoteKind.OST_TOP_50, thisYear) == null) {
            val open = december(thisYear)
            // Open since a week ago, whatever month it is, so the vote can be tried today.
            open.opensAt = minOf(open.opensAt, now.minus(OPEN_FOR))
            if (open.closesAt.isAfter(now)) {
                votes.saveAndFlush(open)
                written++
            }
        }
        return written
    }

    private fun december(year: Int) =
        Vote(
            kind = VoteKind.OST_TOP_50,
            year = year,
            opensAt = LocalDate.of(year, Month.DECEMBER, 1).atStartOfDay(AMSTERDAM).toInstant(),
            closesAt = LocalDateTime.of(year, Month.DECEMBER, LAST_DAY, CLOSES_HOUR, 0).atZone(AMSTERDAM).toInstant(),
            revealAt = LocalDateTime.of(year, Month.DECEMBER, LAST_DAY, REVEAL_HOUR, 0).atZone(AMSTERDAM).toInstant(),
            picks = VoteKind.OST_TOP_50.defaultPicks,
        )

    private fun songOf(
        igdbId: Long,
        title: String,
    ): CandidateTrack? {
        val game = OFFLINE_GAMES.firstOrNull { it.igdbId == igdbId } ?: return null
        val candidate =
            candidates.findByIgdbId(igdbId)
                ?: candidates.saveAndFlush(VoteCandidate(igdbId, game.name, game.firstReleaseYear))
        val externalId = "dev${igdbId.toString().padStart(EXTERNAL_ID_LENGTH, '0')}".takeLast(EXTERNAL_ID_LENGTH)
        return tracks.findByCandidateIdAndSourceAndExternalId(candidate.id!!, TrackSource.YOUTUBE, externalId)
            ?: tracks.saveAndFlush(
                CandidateTrack(candidate, TrackSource.YOUTUBE, externalId, title, clock.instant(), thumbnail = null),
            )
    }

    private companion object {
        val log = LoggerFactory.getLogger(ShippedDevVotes::class.java)!!
        val AMSTERDAM: ZoneId = ZoneId.of("Europe/Amsterdam")
        const val EXTERNAL_ID_LENGTH = 11
        const val VOTER_SPREAD = 5
        const val LAST_DAY = 31
        const val CLOSES_HOUR = 17
        const val REVEAL_HOUR = 20
        val OPEN_FOR: Duration = Duration.ofDays(7)
    }
}
