package net.blueshell.api.pinger.api

import net.blueshell.api.pinger.persistence.PingerContribution
import net.blueshell.api.pinger.persistence.PingerContributionRepository
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

/** The top rate every sender reached together, SiteCie included, and when. */
data class CombinedRecord(
    val pps: Long,
    val at: Instant,
)

/**
 * The public leaderboard: the SiteCie house line, the members ranked by total sent and by peak rate,
 * and the combined record.
 */
data class Leaderboard(
    val house: HouseStanding?,
    val members: List<MemberStanding>,
    val fastest: List<FastestStanding> = emptyList(),
    val record: CombinedRecord? = null,
)

private const val SITECIE_LABEL = "SiteCie"

/**
 * Composes the public leaderboard from the final tallies: it ranks the members by total sent and by
 * peak rate and resolves each to their Discord or site identity. The api decides who is in and in
 * what order; the frontend only formats the rows.
 */
@Service
class LeaderboardService(
    private val contributions: PingerContributionRepository,
    private val identities: MemberIdentities,
    private val faces: DiscordFaces,
    private val records: PingerRecordRepository,
) {
    @Transactional(readOnly = true)
    fun snapshot(): Leaderboard {
        val rows = contributions.findAll()
        val house =
            rows.firstOrNull { it.memberId == null }?.let {
                HouseStanding(
                    label = SITECIE_LABEL,
                    totalSent = it.totalSent,
                    peakPps = it.peakPps,
                    peakAt = it.peakAt,
                )
            }
        // Every contributor is on the board: contributing was the permission, so there is no opt-in
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
                    MemberStanding(
                        rank = index + 1,
                        memberId = id,
                        totalSent = row.totalSent,
                        discord = discord,
                        username = username,
                    )
                }
        // Only a member who reported a rate has one to rank.
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
        return Leaderboard(house = house, members = members, fastest = fastest, record = record)
    }
}
