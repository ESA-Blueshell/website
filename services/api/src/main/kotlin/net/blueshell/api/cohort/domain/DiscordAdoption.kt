package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordRoleKeeper
import net.blueshell.api.discord.api.KeptChannel
import net.blueshell.api.discord.api.KeptChannelKind
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/** A committee or team with no role yet, and the existing role and channel named as it is. */
data class AdoptionMatch(
    val key: String,
    val label: String,
    val type: CohortType,
    val roleId: String,
    val roleName: String,
    val channels: List<KeptChannel>,
)

/** A match as the board saw it: what is linked and opened, so nothing else is. */
data class AdoptionChoice(
    val key: String,
    val roleId: String,
    val channelIds: List<String>,
)

/** A confirmed match the site would not finish: its refusal's code and facts, which the page words. */
data class RefusedMatch(
    val label: String,
    val code: String?,
    val facts: Map<String, Any>,
)

/** How many matches were linked, and the ones refused. */
data class AdoptionOutcome(
    val linked: Int,
    val refused: List<RefusedMatch>,
)

/**
 * Proposes linking committees and teams to the roles already in the server by name, and links the
 * ones the board confirms through the same rules as their forms. Only a role the site could keep and
 * the bot can hand out is proposed, a name two roles share is proposed for neither, and a channel
 * is only offered under the committee's or the team's own category.
 */
@Service
class DiscordAdoption(
    private val cohorts: CohortRepository,
    private val targets: TargetRepository,
    private val targetIds: CohortTargetIds,
    private val roles: DiscordRoleKeeper,
    private val channels: DiscordChannelKeeper,
    private val discord: CohortDiscord,
    private val definitions: CohortDefinitionRegistry,
    @param:Value($$"${discord.committees-category:Committees}") private val committees: String,
    @param:Value($$"${discord.esports-category:Esports}") private val esports: String,
) {
    fun proposals(): List<AdoptionMatch> {
        if (!roles.available() || !channels.available()) return emptyList()
        // A disbanded committee or a deleted team keeps its cohort, which no form could then undo.
        val defined = definitions.all().filter { it.type in ADOPTED }.mapTo(mutableSetOf()) { it.key }
        val linked = targets.findAllBySystem(TargetSystem.DISCORD.name).mapNotNull(targetIds::find).toSet()
        val roleByName =
            roles
                .roles()
                .filter { it.assignable && it.id !in linked }
                .groupBy { plainName(it.name) }
                .filterValues { it.size == 1 }
                .mapValues { it.value.single() }
        val texts = channels.channels().filter { it.kind == KeptChannelKind.TEXT }
        return cohorts
            .findAll()
            .filter { it.definitionKey in defined }
            .filterNot { it.type == CohortType.COMMITTEE_MEMBERS && it.label.equals(CohortDiscord.BOARD_COMMITTEE, ignoreCase = true) }
            .filter { targets.findByCohortIdAndSystem(requireNotNull(it.id), TargetSystem.DISCORD.name)?.let(targetIds::find) == null }
            // A name two of them share says nothing about which one the role is for.
            .groupBy { plainName(it.label) }
            .filterValues { it.size == 1 }
            .values
            .map { it.single() }
            .mapNotNull { cohort ->
                val name = plainName(cohort.label)
                val role = roleByName[name] ?: return@mapNotNull null
                val category = categoryOf(cohort.type)
                AdoptionMatch(
                    key = requireNotNull(cohort.definitionKey),
                    label = cohort.label,
                    type = cohort.type,
                    roleId = role.id,
                    roleName = role.name,
                    channels = texts.filter { plainName(it.name) == name && it.category.equals(category, ignoreCase = true) },
                )
            }.sortedBy { it.label.lowercase() }
    }

    /**
     * Links each confirmed match, only where it is still what the board saw: the same role and the
     * same channels. One refused is named with why, and the rest are still linked.
     */
    fun adopt(choices: Collection<AdoptionChoice>): AdoptionOutcome {
        val seen = choices.associateBy { it.key }
        val confirmed =
            proposals().filter { match ->
                val choice = seen[match.key]
                choice != null && choice.roleId == match.roleId && choice.channelIds.toSet() == match.channels.map { it.id }.toSet()
            }
        val refused = confirmed.mapNotNull(::link)
        // A role linked before its channel was refused is linked all the same.
        val linked = confirmed.count { holdsRole(it.key) }
        return AdoptionOutcome(linked, refused)
    }

    private fun link(match: AdoptionMatch): RefusedMatch? =
        try {
            val choice = DiscordChoice(roleId = match.roleId, channelIds = match.channels.map { it.id })
            discord.apply(match.key, choice, categoryOf(match.type))
            null
        } catch (e: TargetRefusal) {
            RefusedMatch(match.label, e.code, e.facts)
        } catch (e: ResponseStatusException) {
            // Another board member linked it a moment before.
            RefusedMatch(match.label, null, mapOf("reason" to (e.reason ?: "It could not be linked.")))
        }

    private fun holdsRole(key: String): Boolean =
        cohorts
            .findByDefinitionKey(key)
            ?.let { targets.findByCohortIdAndSystem(requireNotNull(it.id), TargetSystem.DISCORD.name) }
            ?.let(targetIds::find) != null

    private fun categoryOf(type: CohortType) = if (type == CohortType.TEAM_PLAYERS) esports else committees
}

private val ADOPTED = setOf(CohortType.COMMITTEE_MEMBERS, CohortType.TEAM_PLAYERS)
