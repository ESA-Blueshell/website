package net.blueshell.api.cohort.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordRoleAccess
import net.blueshell.api.discord.api.DiscordRoleKeeper
import net.blueshell.api.discord.api.KeptChannelKind
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/** The cohorts that span the whole server, in the order the settings page lists them. */
private val KEYS =
    listOf(
        CohortType.CURRENT_MEMBERS.name,
        CohortType.CURRENT_COMMITTEE_MEMBERS.name,
        CohortType.ACTIVISTS.name,
        CohortType.CURRENT_TEAM_PLAYERS.name,
        CohortType.BOARD.name,
        CohortType.KANDI.name,
    )

/** A cohort that spans the whole server, and the Discord role its people hold. */
@Schema(name = "ServerCohortRole")
data class ServerCohortRole(
    val key: String,
    val type: CohortType,
    val label: String,
    val roleId: String?,
    val roleName: String?,
    @param:Schema(description = "The channels the role is opened to when it is set, by name")
    val defaultChannels: List<String>,
    @param:Schema(description = "The channels the role can access now; empty without a role or a bot")
    val channels: List<CohortChannel> = emptyList(),
)

/** A channel a cohort's role can access, as the settings page marks it. */
@Schema(name = "CohortChannel")
data class CohortChannel(
    val id: String,
    val name: String,
    val voice: Boolean,
    @param:Schema(description = "Whether @everyone is kept out of it")
    val private: Boolean,
)

/**
 * The server-wide cohorts, the role each follows and the channels that role opens, as the Discord
 * settings page sets them through the same form a committee's or a team's editor uses. The first time
 * a cohort gets a role, that role is also opened to the cohort's default channels, named in
 * `discord.default-channels` by the cohort's key.
 */
@Service
class ServerCohortRoles(
    private val cohorts: CohortRepository,
    private val targets: TargetRepository,
    private val targetIds: CohortTargetIds,
    private val registrar: CohortRegistrar,
    private val discord: CohortDiscord,
    private val roles: DiscordRoleKeeper,
    private val channels: DiscordChannelKeeper,
    private val access: DiscordRoleAccess,
    @param:Value($$"${discord.committees-category:Committees}") private val committees: String,
    @param:Value($$"${discord.esports-category:Esports}") private val esports: String,
    @param:Value($$"${discord.board-category:Board}") private val boards: String,
    environment: Environment,
) {
    private val defaultsByKey: Map<String, List<String>> =
        Binder
            .get(environment)
            .bind("discord.default-channels", Bindable.mapOf(String::class.java, String::class.java))
            .orElse(emptyMap())
            .orEmpty()
            .mapValues { (_, names) -> names.split(',').map(String::trim).filter(String::isNotEmpty) }

    fun read(): List<ServerCohortRole> {
        // A server-wide cohort added in code has no record until something registers it; the page is that something.
        if (KEYS.any { cohorts.findByDefinitionKey(it) == null }) registrar.register()
        val available = roles.available()
        val readsChannels = available && channels.available()
        val privateIds =
            if (readsChannels) channels.openings().filter { it.private }.mapTo(mutableSetOf()) { it.channel.id } else emptySet()
        return KEYS.mapNotNull { key ->
            val cohort = cohorts.findByDefinitionKey(key) ?: return@mapNotNull null
            val roleId = roleOf(requireNotNull(cohort.id))
            ServerCohortRole(
                key = key,
                type = cohort.type,
                label = cohort.label,
                roleId = roleId,
                roleName = if (available) roleId?.let { roles.role(it)?.name } else null,
                defaultChannels = defaultsByKey[key].orEmpty(),
                channels = if (readsChannels && roleId != null) accessOf(roleId, privateIds) else emptyList(),
            )
        }
    }

    /** The role and channels of the server-wide cohort defined by [key]. */
    fun place(key: String): DiscordPlace = discord.read(serverWide(key))

    /** Sets the role and channels of the server-wide cohort defined by [key], opening its default channels to a role it gets now. */
    fun apply(
        key: String,
        choice: DiscordChoice,
    ): DiscordPlace {
        val before = discord.read(serverWide(key)).roleId
        val after = discord.apply(key, choice, categoryOf(key))
        val roleId = after.roleId
        if (before != null || roleId == null) return after
        openDefaults(key, roleId)
        return discord.read(key)
    }

    private fun accessOf(
        roleId: String,
        privateIds: Set<String>,
    ): List<CohortChannel> =
        channels
            .openedTo(roleId)
            .filter { it.kind != KeptChannelKind.CATEGORY }
            .map { CohortChannel(it.id, it.name, it.kind == KeptChannelKind.VOICE, it.id in privateIds) }

    private fun serverWide(key: String): String {
        if (key !in KEYS) throw ResponseStatusException(HttpStatus.NOT_FOUND, "$key is not a server-wide cohort")
        return key
    }

    private fun categoryOf(key: String): String =
        when (key) {
            CohortType.CURRENT_TEAM_PLAYERS.name -> esports
            CohortType.BOARD.name, CohortType.KANDI.name -> boards
            else -> committees
        }

    private fun openDefaults(
        key: String,
        roleId: String,
    ) {
        val names = defaultsByKey[key].orEmpty().map(String::lowercase).toSet()
        if (names.isEmpty()) return
        channels
            .channels()
            .filter { it.kind != KeptChannelKind.CATEGORY && it.name.lowercase() in names }
            .forEach { access.openForWriting(roleId, it.id) }
    }

    private fun roleOf(cohortId: Long): String? = targets.findByCohortIdAndSystem(cohortId, TargetSystem.DISCORD.name)?.let(targetIds::find)
}
