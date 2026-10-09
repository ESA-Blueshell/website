package net.blueshell.api.pinger.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.pinger.api.FastestStanding
import net.blueshell.api.pinger.api.Leaderboard
import net.blueshell.api.pinger.api.MemberStanding
import java.time.Instant

/**
 * The public leaderboard as a watcher reads it: the SiteCie house line, the members ranked by total
 * sent and by peak rate, the combined record and the combined rate right now.
 */
@Schema(description = "The SiteCie house line, the members ranked by total sent and by peak rate, and the combined record")
data class LeaderboardResponse(
    val house: HouseLineResponse?,
    val members: List<StandingResponse>,
    @field:Schema(description = "The members ranked by the top rate they reached across their devices")
    val fastest: List<FastestResponse>,
    @field:Schema(description = "The top rate every online sender reached together, SiteCie included; null until one is set")
    val record: RecordResponse?,
    @field:Schema(description = "Every online sender's live rate right now, summed, SiteCie included, in pings per second")
    val combinedPps: Long,
) {
    companion object {
        fun from(board: Leaderboard): LeaderboardResponse =
            LeaderboardResponse(
                house =
                    board.house?.let {
                        HouseLineResponse(
                            label = it.label,
                            totalSent = it.totalSent,
                            online = it.online,
                            pps = it.pps,
                            peakPps = it.peakPps,
                            peakAt = it.peakAt,
                        )
                    },
                members = board.members.map { StandingResponse.from(it) },
                fastest = board.fastest.map { FastestResponse.from(it) },
                record = board.record?.let { RecordResponse(pps = it.pps, at = it.at) },
                combinedPps = board.combinedPps,
            )
    }
}

/** The combined record: the top rate every online sender reached together, and when. */
@Schema(description = "The top rate every online sender reached together, SiteCie included, and when")
data class RecordResponse(
    @field:Schema(description = "The combined rate, in pings per second")
    val pps: Long,
    val at: Instant,
)

/** One member on the fastest board, shown the way [StandingResponse] shows them. */
@Schema(description = "One member ranked by peak rate: their Discord tag and avatar when linked, otherwise their site username")
data class FastestResponse(
    val rank: Int,
    val memberId: Long,
    @field:Schema(description = "The top rate the member reached across their devices, in pings per second")
    val peakPps: Long,
    val peakAt: Instant?,
    val discordTag: String?,
    val avatarUrl: String?,
    val username: String?,
) {
    companion object {
        fun from(standing: FastestStanding): FastestResponse =
            FastestResponse(
                rank = standing.rank,
                memberId = standing.memberId,
                peakPps = standing.peakPps,
                peakAt = standing.peakAt,
                discordTag = standing.discord?.tag,
                avatarUrl = standing.discord?.avatarUrl,
                username = standing.username,
            )
    }
}

/** The SiteCie house line, a labelled total outside the member ranking. */
@Schema(description = "The SiteCie house line, a labelled total shown outside the member ranking")
data class HouseLineResponse(
    val label: String,
    val totalSent: Long,
    val online: Boolean,
    @field:Schema(description = "The live rate across SiteCie's replicas right now, in pings per second")
    val pps: Int,
    @field:Schema(description = "The top rate SiteCie reached across its replicas, in pings per second")
    val peakPps: Long,
    val peakAt: Instant?,
)

/**
 * One ranked member. [discordTag] and [avatarUrl] are set together when the member's Discord is
 * linked and visible; [username] is set instead when it is not. The frontend shows whichever is
 * present.
 */
@Schema(description = "One ranked member: their Discord tag and avatar when linked, otherwise their site username")
data class StandingResponse(
    val rank: Int,
    val memberId: Long,
    val totalSent: Long,
    val online: Boolean,
    @field:Schema(description = "The member's live rate across their devices right now, in pings per second")
    val pps: Int,
    val discordTag: String?,
    val avatarUrl: String?,
    val username: String?,
) {
    companion object {
        fun from(standing: MemberStanding): StandingResponse =
            StandingResponse(
                rank = standing.rank,
                memberId = standing.memberId,
                totalSent = standing.totalSent,
                online = standing.online,
                pps = standing.pps,
                discordTag = standing.discord?.tag,
                avatarUrl = standing.discord?.avatarUrl,
                username = standing.username,
            )
    }
}
