package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordRoleKeeper
import net.blueshell.api.discord.api.DiscordUnavailable
import net.blueshell.api.discord.api.KeptChannel
import net.blueshell.api.discord.api.KeptChannelKind
import net.blueshell.api.shared.enums.TargetSystem
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/** A cohort's place on Discord: the role its people hold, and the channels that role opens. */
@Schema(name = "DiscordPlace")
data class DiscordPlace(
    /** Whether the bot is in the server; nothing below is read without it. */
    val available: Boolean,
    val roleId: String?,
    val roleName: String?,
    val channels: List<KeptChannel>,
)

/**
 * What a form asks of Discord. The role is linked once, to [roleId] or to a new role where
 * [createRole]; a cohort already holding one keeps it. [channelIds] is every channel the role should
 * open, and [createChannel] names a new private channel to make for it.
 */
data class DiscordChoice(
    val roleId: String? = null,
    val createRole: Boolean = false,
    val channelIds: List<String> = emptyList(),
    val createChannel: String? = null,
)

/**
 * A cohort's role and private channels, as a committee's or a team's form sets them. The role is the
 * cohort's Discord target, so it follows the cohort's people; a channel is opened to that role by an
 * overwrite and kept from everybody else, under the category the caller names when the site makes it.
 */
@Service
class CohortDiscord(
    private val cohorts: CohortRepository,
    private val targets: TargetRepository,
    private val targetIds: CohortTargetIds,
    private val targeting: CohortTargeting,
    private val registrar: CohortRegistrar,
    private val roles: DiscordRoleKeeper,
    private val channels: DiscordChannelKeeper,
) {
    fun read(key: String): DiscordPlace {
        if (!roles.available() || !channels.available()) return DiscordPlace(false, null, null, emptyList())
        val roleId = roleOf(cohortIdOf(key))
        return unavailableAsRefusal {
            DiscordPlace(true, roleId, roleId?.let { roles.role(it)?.name }, roleId?.let(channels::openedTo).orEmpty())
        }
    }

    /** Sets the role and channels of the cohort defined by [key], a new channel going under [category]. */
    fun apply(
        key: String,
        choice: DiscordChoice,
        category: String,
    ): DiscordPlace {
        if (!roles.available() || !channels.available()) throw TargetSystemUnavailable(TargetSystem.DISCORD)
        val cohortId = cohortIdOf(key)
        val roleId =
            roleOf(cohortId)
                ?: when {
                    choice.roleId != null -> targeting.linkExisting(cohortId, TargetSystem.DISCORD, choice.roleId).externalId
                    choice.createRole -> targeting.create(cohortId, TargetSystem.DISCORD, labelOf(cohortId), null).externalId
                    else -> null
                }
                ?: return read(key)
        unavailableAsRefusal {
            val open = channels.openedTo(roleId).map { it.id }.toSet()
            (choice.channelIds - open).forEach { channels.open(it, roleId, private = true) }
            (open - choice.channelIds.toSet()).forEach { channels.close(it, roleId) }
            choice.createChannel
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { channels.createPrivate(it, category, roleId) }
        }
        return read(key)
    }

    /**
     * Takes the cohort's role and the private channels it opens off Discord, and the role off the
     * cohort: a deliberate removal, never what archiving does.
     */
    fun remove(key: String) {
        if (!roles.available() || !channels.available()) throw TargetSystemUnavailable(TargetSystem.DISCORD)
        val target = targets.findByCohortIdAndSystem(cohortIdOf(key), TargetSystem.DISCORD.name) ?: return
        val roleId = targetIds.find(target)
        unavailableAsRefusal {
            roleId?.let { role ->
                channels.openedTo(role).filter { it.kind != KeptChannelKind.CATEGORY }.forEach { channels.delete(it.id) }
                roles.delete(role)
            }
        }
        targets.delete(target)
    }

    // A record made a moment ago may not have its cohort yet, so one is registered for it.
    private fun cohortIdOf(key: String): Long {
        val cohort =
            cohorts.findByDefinitionKey(key)
                ?: registrar.register().let { cohorts.findByDefinitionKey(key) }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No cohort is defined as $key")
        return requireNotNull(cohort.id)
    }

    private fun labelOf(cohortId: Long) = cohorts.findById(cohortId).map { it.label }.orElseThrow()

    private fun roleOf(cohortId: Long): String? = targets.findByCohortIdAndSystem(cohortId, TargetSystem.DISCORD.name)?.let(targetIds::find)

    private fun <T> unavailableAsRefusal(call: () -> T): T =
        try {
            call()
        } catch (e: DiscordUnavailable) {
            throw TargetSystemUnavailable(TargetSystem.DISCORD).apply { initCause(e) }
        }
}
