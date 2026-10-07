package net.blueshell.api.pinger.api

import net.blueshell.api.pinger.persistence.PingerBoardMemberRepository
import net.blueshell.api.pinger.persistence.PingerContribution
import net.blueshell.api.pinger.persistence.PingerContributionRepository
import net.blueshell.api.pinger.persistence.PingerLiveStore
import net.blueshell.api.shared.discord.DiscordFaces
import net.blueshell.api.shared.user.MemberIdentities
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** The SiteCie house line, shown apart from the member ranking rather than within it. */
data class HouseStanding(
    val label: String,
    val totalSent: Long,
    val online: Boolean,
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
    val discord: DiscordStanding?,
    val username: String?,
)

/** A snapshot of the public leaderboard: the SiteCie house line and the ranked, opted-in members. */
data class Leaderboard(
    val house: HouseStanding?,
    val members: List<MemberStanding>,
)

private const val SITECIE_LABEL = "SiteCie"

/**
 * Composes the public leaderboard from the durable tallies: it ranks the opted-in members by total
 * sent, resolves each to their Discord or site identity, and reads live presence from Valkey. The
 * api decides who is in and in what order; the frontend only formats the rows.
 */
@Service
class LeaderboardService(
    private val contributions: PingerContributionRepository,
    private val optIns: PingerBoardMemberRepository,
    private val identities: MemberIdentities,
    private val faces: DiscordFaces,
    private val live: PingerLiveStore,
) {
    @Transactional(readOnly = true)
    fun snapshot(): Leaderboard {
        val rows = contributions.findAll()
        val house =
            rows.firstOrNull { it.memberId == null }?.let {
                HouseStanding(label = SITECIE_LABEL, totalSent = it.totalSent, online = onlineOf(it.identity))
            }
        val optedIn = optIns.findOptedInMemberIds().toSet()
        val included = rows.filter { it.memberId != null && it.memberId in optedIn }
        val byId = identities.of(included.mapNotNull { it.memberId })
        val seen = faces.of(byId.values.mapNotNull { it.discordId })
        val members =
            included
                .filter { byId.containsKey(it.memberId) }
                .sortedWith(compareByDescending<PingerContribution> { it.totalSent }.thenBy { it.memberId })
                .mapIndexed { index, row ->
                    val id = row.memberId!!
                    val identity = byId.getValue(id)
                    val face = identity.discordId?.let(seen::get)
                    MemberStanding(
                        rank = index + 1,
                        memberId = id,
                        totalSent = row.totalSent,
                        online = onlineOf(row.identity),
                        discord = face?.let { DiscordStanding(tag = it.name, avatarUrl = it.avatar) },
                        username = if (face == null) identity.username else null,
                    )
                }
        return Leaderboard(house = house, members = members)
    }

    private fun onlineOf(key: String): Boolean = live.find(key)?.online ?: false
}
