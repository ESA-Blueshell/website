package net.blueshell.api.cohort.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordRoleKeeper
import net.blueshell.api.discord.api.KeptChannelKind
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/** A role a row will have: an existing one by its id, or a new one by the name it will get. */
@Schema(name = "PlannedRole")
data class PlannedRole(
    @param:Schema(description = "The existing role, or null for a role that will be created")
    val roleId: String?,
    val name: String,
    @param:Schema(description = "Whether the cohort follows this role already")
    val kept: Boolean,
    @param:Schema(description = "The channels an existing role has access to now, which applying the row keeps")
    val opens: List<String> = emptyList(),
)

/** A channel a row will have: an existing one by its id, or a new one by the name and category it will get. */
@Schema(name = "PlannedChannel")
data class PlannedChannel(
    @param:Schema(description = "The existing channel, or null for a channel that will be created")
    val channelId: String?,
    val name: String,
    val category: String?,
)

/** What one row of a bulk add will do on Discord, worked out from the server as it is now. */
@Schema(name = "DiscordPlanRow")
data class DiscordPlanRow(
    val key: String,
    val label: String,
    val role: PlannedRole,
    @param:Schema(description = "The channel the row gets, or null where it asked for none")
    val channel: PlannedChannel?,
    @param:Schema(description = "The category a channel the row creates goes under")
    val category: String,
)

/** One row a bulk add asks about: the cohort, and the channel it wants by name, or none. */
data class PlanAsk(
    val key: String,
    val channel: String?,
)

/**
 * Works out what a bulk add of Discord roles and channels will do, before anything is done. A role
 * or a text channel already in the server under the same plain name is linked, and only what is
 * missing will be created; the same matching [CohortDiscord.apply] uses, so the plan is what happens.
 */
@Service
class DiscordPlanner(
    private val cohorts: CohortRepository,
    private val targets: TargetRepository,
    private val targetIds: CohortTargetIds,
    private val registrar: CohortRegistrar,
    private val roles: DiscordRoleKeeper,
    private val channels: DiscordChannelKeeper,
    @param:Value($$"${discord.committees-category:Committees}") private val committees: String,
    @param:Value($$"${discord.esports-category:Esports}") private val esports: String,
    @param:Value($$"${discord.board-category:Board}") private val boards: String,
) {
    fun plan(asks: List<PlanAsk>): List<DiscordPlanRow> {
        if (!roles.available() || !channels.available()) throw TargetSystemUnavailable(TargetSystem.DISCORD)
        if (asks.any { cohorts.findByDefinitionKey(it.key) == null }) registrar.register()
        val linked = targets.findAllBySystem(TargetSystem.DISCORD.name).mapNotNull(targetIds::find).toSet()
        val keepable = roles.roles()
        val texts = channels.channels().filter { it.kind == KeptChannelKind.TEXT }
        return asks.mapNotNull { ask ->
            val cohort = cohorts.findByDefinitionKey(ask.key) ?: return@mapNotNull null
            val held = targets.findByCohortIdAndSystem(requireNotNull(cohort.id), TargetSystem.DISCORD.name)?.let(targetIds::find)
            val role =
                held?.let { id -> PlannedRole(id, keepable.firstOrNull { it.id == id }?.name ?: id, kept = true) }
                    ?: keepable
                        .firstOrNull { it.id !in linked && plainName(it.name) == plainName(cohort.label) }
                        ?.let { PlannedRole(it.id, it.name, kept = false) }
                    ?: PlannedRole(null, cohort.label, kept = false)
            val opensNow = role.roleId?.let { id -> channels.openedTo(id).filter { it.kind != KeptChannelKind.CATEGORY }.map { it.id } }
            val category = categoryOf(cohort.type)
            val channel =
                ask.channel?.trim()?.removePrefix("#")?.takeIf { it.isNotEmpty() }?.let { wanted ->
                    texts
                        .firstOrNull { plainName(it.name) == plainName(wanted) }
                        ?.let { PlannedChannel(it.id, it.name, it.category) }
                        ?: PlannedChannel(null, wanted, category)
                }
            DiscordPlanRow(ask.key, cohort.label, role.copy(opens = opensNow.orEmpty()), channel, category)
        }
    }

    private fun categoryOf(type: CohortType): String =
        when (type) {
            CohortType.TEAM_PLAYERS, CohortType.CURRENT_TEAM_PLAYERS -> esports
            CohortType.BOARD, CohortType.KANDI, CohortType.BOARD_YEAR_MEMBERS -> boards
            else -> committees
        }
}

/** A name as Discord's matching here reads it: lowercase letters and digits, everything else dropped. */
internal fun plainName(name: String) = name.lowercase().filter { it.isLetterOrDigit() }
