package net.blueshell.api.pinger.api

import net.blueshell.api.pinger.persistence.PingerContribution
import net.blueshell.api.pinger.persistence.PingerContributionRepository
import net.blueshell.api.pinger.persistence.PingerLive
import net.blueshell.api.pinger.persistence.PingerLiveStore
import net.blueshell.api.pinger.persistence.PingerRecordRepository
import net.blueshell.api.shared.discord.DiscordFaces
import net.blueshell.api.shared.user.MemberIdentities
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** The SiteCie house line, shown apart from the member ranking rather than within it. */
data class HouseStanding(
    val label: String,
    val totalSent: Long,
    val online: Boolean,
    /** The live rate across SiteCie's replicas right now, summed. */
    val pps: Int,
    val peakPps: Long = 0,
    val peakAt: Instant? = null,
)

/**
 * How a member is shown when their Discord is linked and the server can see them: the name the
 * server shows ([tag]) and their avatar. Never a real or legal name.
 */
data class DiscordStanding(
    val tag: String,
    val avatarUrl: String,
)

/**
 * A member's standing in the ranking. Exactly one identity is set: [discord] when their Discord is
 * linked and visible, otherwise the site [username].
 */
data class MemberStanding(
    val rank: Int,
    val memberId: Long,
    val totalSent: Long,
    val online: Boolean,
    /** The member's live rate across their devices right now, summed. */
    val pps: Int,
    val discord: DiscordStanding?,
    val username: String?,
)

/** A member's place on the fastest board: their top rate across their devices and when they set it. */
data class FastestStanding(
    val rank: Int,
    val memberId: Long,
    val peakPps: Long,
    val peakAt: Instant?,
    val discord: DiscordStanding?,
    val username: String?,
)

/** The top rate every online sender reached together, SiteCie included, and when. */
data class CombinedRecord(
    val pps: Long,
    val at: Instant,
)

/**
 * A snapshot of the public leaderboard: the SiteCie house line, the members ranked by total sent and
 * by peak rate, the combined record and the combined rate right now.
 */
data class Leaderboard(
    val house: HouseStanding?,
    val members: List<MemberStanding>,
    val fastest: List<FastestStanding> = emptyList(),
    val record: CombinedRecord? = null,
    val combinedPps: Long = 0,
)

private const val SITECIE_LABEL = "SiteCie"

/** Every online sender's live rate summed, SiteCie included: the rate the combined record is set from. */
internal fun Map<String, PingerLive>.combinedPps(): Long = values.filter { it.online }.sumOf { it.pps.toLong() }

/**
 * Composes the public leaderboard from the durable tallies: it ranks the opted-in members by total
 * sent, resolves each to their Discord or site identity, and reads live presence from Valkey. The
 * api decides who is in and in what order; the frontend only formats the rows.
 */
@Service
class LeaderboardService(
    private val contributions: PingerContributionRepository,
    private val identities: MemberIdentities,
    private val faces: DiscordFaces,
    private val live: PingerLiveStore,
    private val records: PingerRecordRepository,
) {
    @Transactional(readOnly = true)
    fun snapshot(): Leaderboard {
        val rows = contributions.findAll()
        // One Valkey scan for every identity's presence, rather than one scan per contributor.
        val presence = live.aggregateAll()
        val house =
            rows.firstOrNull { it.memberId == null }?.let {
                val here = presence[it.identity]
                HouseStanding(
                    label = SITECIE_LABEL,
                    totalSent = it.totalSent,
                    online = here?.online ?: false,
                    pps = here?.pps ?: 0,
                    peakPps = it.peakPps,
                    peakAt = it.peakAt,
                )
            }
        // Every contributor is on the board: contributing is the permission, so there is no opt-in
        // to filter on. A member is shown by their public identity, never a real name.
        val included = rows.filter { it.memberId != null }
        val byId = identities.of(included.mapNotNull { it.memberId })
        val seen = faces.of(byId.values.mapNotNull { it.discordId })
        val shown = included.filter { byId.containsKey(it.memberId) }

        // A member's Discord face where it is linked and visible, otherwise their site username.
        fun faceOf(id: Long): Pair<DiscordStanding?, String?> {
            val identity = byId.getValue(id)
            val face = identity.discordId?.let(seen::get) ?: return null to identity.username
            return DiscordStanding(tag = face.name, avatarUrl = face.avatar) to null
        }

        val members =
            shown
                .sortedWith(compareByDescending<PingerContribution> { it.totalSent }.thenBy { it.memberId })
                .mapIndexed { index, row ->
                    val id = row.memberId!!
                    val (discord, username) = faceOf(id)
                    val here = presence[row.identity]
                    MemberStanding(
                        rank = index + 1,
                        memberId = id,
                        totalSent = row.totalSent,
                        online = here?.online ?: false,
                        pps = here?.pps ?: 0,
                        discord = discord,
                        username = username,
                    )
                }
        // Only a member who has sent while online has a rate to rank.
        val fastest =
            shown
                .filter { it.peakPps > 0 }
                .sortedWith(compareByDescending<PingerContribution> { it.peakPps }.thenBy { it.memberId })
                .mapIndexed { index, row ->
                    val id = row.memberId!!
                    val (discord, username) = faceOf(id)
                    FastestStanding(
                        rank = index + 1,
                        memberId = id,
                        peakPps = row.peakPps,
                        peakAt = row.peakAt,
                        discord = discord,
                        username = username,
                    )
                }
        val record =
            records.findById(PingerRecordRepository.RECORD_ID).orElse(null)?.let { stored ->
                stored.setAt?.takeIf { stored.pps > 0 }?.let { CombinedRecord(pps = stored.pps, at = it) }
            }
        return Leaderboard(house = house, members = members, fastest = fastest, record = record, combinedPps = presence.combinedPps())
    }
}
