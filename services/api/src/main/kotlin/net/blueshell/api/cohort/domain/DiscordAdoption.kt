package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordRoleKeeper
import net.blueshell.api.discord.api.KeptChannel
import net.blueshell.api.discord.api.KeptChannelKind
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.stereotype.Service

/** A committee, team, board or board year with no role yet, and the existing role and channels named as it is. */
data class AdoptionMatch(
    val key: String,
    val label: String,
    val type: CohortType,
    val roleId: String,
    val roleName: String,
    val channels: List<KeptChannel>,
)

/** A confirmed match Discord would not let the site finish, and why. */
data class RefusedMatch(
    val label: String,
    val reason: String,
)

/** How many matches were linked, and the ones Discord refused. */
data class AdoptionOutcome(
    val linked: Int,
    val refused: List<RefusedMatch>,
)

/**
 * Proposes linking committees, teams, the board, Kandi and board years to the roles already in the server by name, and
 * links the ones the board confirms. Only roles the site could keep are proposed, so the claim bot's
 * never are, and a name two roles share is proposed for neither.
 */
@Service
class DiscordAdoption(
    private val cohorts: CohortRepository,
    private val targets: TargetRepository,
    private val targetIds: CohortTargetIds,
    private val roles: DiscordRoleKeeper,
    private val channels: DiscordChannelKeeper,
    private val discord: CohortDiscord,
    private val registrar: CohortRegistrar,
) {
    fun proposals(): List<AdoptionMatch> {
        if (!roles.available() || !channels.available()) return emptyList()
        // A definition newer than the last sweep, such as a board year, has no cohort yet and so nothing to match.
        registrar.register()
        val linked = targets.findAllBySystem(TargetSystem.DISCORD.name).mapNotNull(targetIds::find).toSet()
        val roleByName =
            roles
                .roles()
                .filter { it.id !in linked }
                .groupBy { plain(it.name) }
                .filterValues { it.size == 1 }
                .mapValues { it.value.single() }
        val texts = channels.channels().filter { it.kind == KeptChannelKind.TEXT }
        return cohorts
            .findAll()
            .filter { it.type in ADOPTED && it.definitionKey != null }
            .filterNot { it.type == CohortType.COMMITTEE_MEMBERS && it.label.equals(CohortDiscord.BOARD_COMMITTEE, ignoreCase = true) }
            .filter { cohort ->
                targets.findByCohortIdAndSystem(requireNotNull(cohort.id), TargetSystem.DISCORD.name)?.let(targetIds::find) ==
                    null
            }
            // Where two are called the same, the board's own cohort is matched before a committee of that name.
            .sortedBy { ADOPTED.indexOf(it.type) }
            .distinctBy { plain(it.label) }
            .mapNotNull { cohort ->
                val name = plain(cohort.label)
                val role = roleByName[name] ?: return@mapNotNull null
                AdoptionMatch(
                    requireNotNull(cohort.definitionKey),
                    cohort.label,
                    cohort.type,
                    role.id,
                    role.name,
                    // A board's role keeps the channels it has; only a committee or a team takes the channel named as it is.
                    if (cohort.type in NAMED_CHANNELS) texts.filter { plain(it.name) == name } else emptyList(),
                )
            }.sortedBy { it.label.lowercase() }
    }

    /**
     * Links each confirmed match. What the role has access to already is left as it is, and the
     * channels named as the match is are opened to it. A match Discord refuses is named with why, and
     * the rest are still linked.
     */
    fun adopt(keys: Collection<String>): AdoptionOutcome {
        val confirmed = proposals().filter { it.key in keys }
        val refused =
            confirmed.mapNotNull { match ->
                val opened = channels.openedTo(match.roleId).filter { it.kind != KeptChannelKind.CATEGORY }.map { it.id }
                val channelIds = (opened + match.channels.map { it.id }).distinct()
                try {
                    // Nothing is made, so the category for a new channel is never read.
                    discord.apply(match.key, DiscordChoice(roleId = match.roleId, channelIds = channelIds), category = "")
                    null
                } catch (e: TargetSystemRefused) {
                    RefusedMatch(match.label, e.reason)
                }
            }
        return AdoptionOutcome(confirmed.size - refused.size, refused)
    }

    private fun plain(name: String) = name.lowercase().filter { it.isLetterOrDigit() }
}

/** The cohorts a channel of the same name is offered with. */
private val NAMED_CHANNELS = setOf(CohortType.COMMITTEE_MEMBERS, CohortType.TEAM_PLAYERS)

/** What is matched, in the order it is: a role called after a board goes to the board. */
private val ADOPTED = listOf(CohortType.BOARD, CohortType.KANDI, CohortType.BOARD_YEAR_MEMBERS) + NAMED_CHANNELS
