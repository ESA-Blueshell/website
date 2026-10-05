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

/** A committee, team or board year with no role yet, and the existing role and channels named as it is. */
data class AdoptionMatch(
    val key: String,
    val label: String,
    val type: CohortType,
    val roleId: String,
    val roleName: String,
    val channels: List<KeptChannel>,
)

/**
 * Proposes linking committees, teams and board years to the roles and channels already in the server by name, and
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
) {
    fun proposals(): List<AdoptionMatch> {
        if (!roles.available() || !channels.available()) return emptyList()
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
            .filter { cohort ->
                targets.findByCohortIdAndSystem(requireNotNull(cohort.id), TargetSystem.DISCORD.name)?.let(targetIds::find) ==
                    null
            }.mapNotNull { cohort ->
                val name = plain(cohort.label)
                val role = roleByName[name] ?: return@mapNotNull null
                AdoptionMatch(
                    requireNotNull(cohort.definitionKey),
                    cohort.label,
                    cohort.type,
                    role.id,
                    role.name,
                    texts.filter {
                        plain(it.name) ==
                            name
                    },
                )
            }.sortedBy { it.label.lowercase() }
    }

    /** Links each confirmed match, keeping every channel its role opens already; answers how many were linked. */
    fun adopt(keys: Collection<String>): Int {
        val confirmed = proposals().filter { it.key in keys }
        confirmed.forEach { match ->
            val opened = channels.openedTo(match.roleId).filter { it.kind != KeptChannelKind.CATEGORY }.map { it.id }
            val channelIds = (opened + match.channels.map { it.id }).distinct()
            // Nothing is made, so the category for a new channel is never read.
            discord.apply(match.key, DiscordChoice(roleId = match.roleId, channelIds = channelIds), category = "")
        }
        return confirmed.size
    }

    private fun plain(name: String) = name.lowercase().filter { it.isLetterOrDigit() }

    private companion object {
        val ADOPTED = setOf(CohortType.COMMITTEE_MEMBERS, CohortType.TEAM_PLAYERS, CohortType.BOARD_YEAR_MEMBERS)
    }
}
