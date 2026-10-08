package net.blueshell.api.pinger.api

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
    /** The live rate across SiteCie's replicas right now, summed. */
    val pps: Int,
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
    private val identities: MemberIdentities,
    private val faces: DiscordFaces,
    private val live: PingerLiveStore,
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
                )
            }
        // Every contributor is on the board: contributing is the permission, so there is no opt-in
        // to filter on. A member is shown by their public identity, never a real name.
        val included = rows.filter { it.memberId != null }
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
                    val here = presence[row.identity]
                    MemberStanding(
                        rank = index + 1,
                        memberId = id,
                        totalSent = row.totalSent,
                        online = here?.online ?: false,
                        pps = here?.pps ?: 0,
                        discord = face?.let { DiscordStanding(tag = it.name, avatarUrl = it.avatar) },
                        username = if (face == null) identity.username else null,
                    )
                }
        return Leaderboard(house = house, members = members)
    }
}
