package net.blueshell.api.pinger.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.pinger.api.Leaderboard
import net.blueshell.api.pinger.api.MemberStanding

/** The public leaderboard as a watcher reads it: the SiteCie house line and the ranked members. */
@Schema(description = "The SiteCie house line and the ranked, opted-in members of the contribution leaderboard")
data class LeaderboardResponse(
    val house: HouseLineResponse?,
    val members: List<StandingResponse>,
) {
    companion object {
        fun from(board: Leaderboard): LeaderboardResponse =
            LeaderboardResponse(
                house = board.house?.let { HouseLineResponse(label = it.label, totalSent = it.totalSent, online = it.online) },
                members = board.members.map { StandingResponse.from(it) },
            )
    }
}

/** The SiteCie house line, a labelled total outside the member ranking. */
@Schema(description = "The SiteCie house line, a labelled total shown outside the member ranking")
data class HouseLineResponse(
    val label: String,
    val totalSent: Long,
    val online: Boolean,
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
                discordTag = standing.discord?.tag,
                avatarUrl = standing.discord?.avatarUrl,
                username = standing.username,
            )
    }
}

/** Whether the signed-in member appears on the public leaderboard. */
@Schema(description = "Whether the signed-in member appears on the public leaderboard")
data class OptInResponse(
    val optedIn: Boolean,
)

/** The signed-in member's choice to appear on the public leaderboard or not. */
@Schema(description = "The member's choice to appear on the public leaderboard")
data class OptInRequest(
    val optedIn: Boolean,
)
