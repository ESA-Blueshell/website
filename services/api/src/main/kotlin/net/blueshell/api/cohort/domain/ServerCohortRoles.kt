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
)

/**
 * The server-wide cohorts and the role each follows, as the Discord settings page sets them. Setting
 * a role links it, or moves the cohort to it from the role it had; Discord keeps both roles, and the
 * cohort's people are reconciled onto the new one. Each time, the role is opened to the cohort's
 * default channels, named in `discord.default-channels` by the cohort's key.
 */
@Service
class ServerCohortRoles(
    private val cohorts: CohortRepository,
    private val targets: TargetRepository,
    private val targetIds: CohortTargetIds,
    private val targeting: CohortTargeting,
    private val registrar: CohortRegistrar,
    private val roles: DiscordRoleKeeper,
    private val channels: DiscordChannelKeeper,
    private val access: DiscordRoleAccess,
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
            )
        }
    }

    /** Points the cohort defined by [key] at the role [roleId], or at a new role named after it where [create]. */
    fun set(
        key: String,
        roleId: String?,
        create: Boolean,
    ): List<ServerCohortRole> {
        if (key !in KEYS) throw ResponseStatusException(HttpStatus.NOT_FOUND, "$key is not a server-wide cohort")
        if (!roles.available() || !channels.available()) throw TargetSystemUnavailable(TargetSystem.DISCORD)
        val cohort =
            cohorts.findByDefinitionKey(key)
                ?: registrar.register().let { cohorts.findByDefinitionKey(key) }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No cohort is defined as $key")
        val cohortId = requireNotNull(cohort.id)
        val target = targets.findByCohortIdAndSystem(cohortId, TargetSystem.DISCORD.name)
        val linked =
            when {
                roleId != null && target != null && targetIds.find(target) != null ->
                    targeting.switchTarget(cohortId, requireNotNull(target.id), roleId, deletePrevious = false, reconcileNow = true)
                roleId != null -> targeting.linkExisting(cohortId, TargetSystem.DISCORD, roleId)
                create -> targeting.create(cohortId, TargetSystem.DISCORD, cohort.label, null)
                else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Name a role or ask for a new one")
            }
        linked.externalId?.let { openDefaults(key, it) }
        return read()
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
