package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordRoleKeeper
import net.blueshell.api.discord.api.DiscordUnavailable
import net.blueshell.api.discord.api.KeptChannel
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/** A committee's place on Discord: the role its seats hold, and the channels that role opens. */
data class CommitteeDiscordState(
    /** Whether the bot is in the server; nothing below is read without it. */
    val available: Boolean,
    val roleId: String?,
    val roleName: String?,
    val channels: List<KeptChannel>,
)

/**
 * What the committee form asks of Discord. The role is linked once, to [roleId] or to a new role
 * where [createRole]; a committee already holding one keeps it. [channelIds] is every channel the
 * role should open, and [createChannel] names a new private channel to make for it.
 */
data class CommitteeDiscordChoice(
    val roleId: String? = null,
    val createRole: Boolean = false,
    val channelIds: List<String> = emptyList(),
    val createChannel: String? = null,
)

/**
 * A committee's role and private channels. The role is the committee-members cohort's Discord target,
 * so it follows the seats; a channel is opened to that role by an overwrite, and kept from everybody
 * else, under the Committees category when the site makes it.
 */
@Service
class CommitteeDiscord(
    private val cohorts: CohortRepository,
    private val targets: TargetRepository,
    private val targetIds: CohortTargetIds,
    private val targeting: CohortTargeting,
    private val registrar: CohortRegistrar,
    private val roles: DiscordRoleKeeper,
    private val channels: DiscordChannelKeeper,
    @param:Value($$"${discord.committees-category:Committees}") private val category: String,
) {
    fun read(committeeId: Long): CommitteeDiscordState {
        if (!roles.available() || !channels.available()) return CommitteeDiscordState(false, null, null, emptyList())
        val roleId = roleOf(cohortIdOf(committeeId))
        return unavailableAsRefusal {
            CommitteeDiscordState(true, roleId, roleId?.let { roles.role(it)?.name }, roleId?.let(channels::openedTo).orEmpty())
        }
    }

    fun apply(
        committeeId: Long,
        choice: CommitteeDiscordChoice,
    ): CommitteeDiscordState {
        if (!roles.available() || !channels.available()) throw TargetSystemUnavailable(TargetSystem.DISCORD)
        val cohortId = cohortIdOf(committeeId)
        val roleId =
            roleOf(cohortId)
                ?: when {
                    choice.roleId != null -> targeting.linkExisting(cohortId, TargetSystem.DISCORD, choice.roleId).externalId
                    choice.createRole -> targeting.create(cohortId, TargetSystem.DISCORD, labelOf(cohortId), null).externalId
                    else -> null
                }
                ?: return read(committeeId)
        unavailableAsRefusal {
            val open = channels.openedTo(roleId).map { it.id }.toSet()
            (choice.channelIds - open).forEach { channels.open(it, roleId, private = true) }
            (open - choice.channelIds.toSet()).forEach { channels.close(it, roleId) }
            choice.createChannel?.trim()?.takeIf { it.isNotEmpty() }?.let { channels.createPrivate(it, category, roleId) }
        }
        return read(committeeId)
    }

    // A committee made a moment ago may not have its cohort yet, so one is registered for it.
    private fun cohortIdOf(committeeId: Long): Long {
        val key = "${CohortType.COMMITTEE_MEMBERS}:$committeeId"
        val cohort =
            cohorts.findByDefinitionKey(key)
                ?: registrar.register().let { cohorts.findByDefinitionKey(key) }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Committee $committeeId has no cohort")
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
