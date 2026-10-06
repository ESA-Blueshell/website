package net.blueshell.api.cohort.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordRefused
import net.blueshell.api.discord.api.DiscordRoleKeeper
import net.blueshell.api.discord.api.DiscordUnavailable
import net.blueshell.api.discord.api.KeptChannel
import net.blueshell.api.discord.api.KeptChannelKind
import net.blueshell.api.shared.enums.TargetSystem
import org.slf4j.LoggerFactory
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
 * open, and [createChannel] names a new private channel to make for it. A role another cohort follows
 * is refused unless [move] takes it from that cohort.
 */
data class DiscordChoice(
    val roleId: String? = null,
    val createRole: Boolean = false,
    val channelIds: List<String> = emptyList(),
    val createChannel: String? = null,
    /** Takes [roleId] from the cohort that follows it now, rather than refusing. */
    val move: Boolean = false,
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
        if (isBoardCommittee(cohortId) && (choice.roleId != null || choice.createRole)) throw BoardCommitteeHasNoRole()
        if (roleOf(cohortId) == null && choice.roleId != null) takeFromOthers(cohortId, choice.roleId, choice.move)
        val roleId = roleOf(cohortId) ?: roleFor(cohortId, choice) ?: return read(key)
        unavailableAsRefusal {
            val opened = channels.openedTo(roleId)
            (choice.channelIds - opened.map { it.id }.toSet()).forEach { channels.open(it, roleId, private = true) }
            // A category is opened from the role's own page and never named on a form, so one left out is kept.
            opened
                .filter { it.kind != KeptChannelKind.CATEGORY && it.id !in choice.channelIds }
                .forEach { channels.close(it.id, roleId) }
            choice.createChannel
                ?.trim()
                ?.removePrefix("#")
                ?.takeIf { it.isNotEmpty() }
                ?.let { createOrOpen(it, category, roleId, held = choice.channelIds) }
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

    /**
     * Takes [roleId] off the cohort that follows it and changes nothing on Discord: the role, its
     * holders and its channels stay. The way back from a role linked to the wrong cohort.
     */
    fun unlink(roleId: String) {
        targets.findAllBySystem(TargetSystem.DISCORD.name).firstOrNull { targetIds.find(it) == roleId }?.let(targets::delete)
    }

    /**
     * Moves the private channels the cohort's role opens into the archive category, or back out.
     * Without a bot nothing moves, and that is said in the log rather than failing the archive.
     */
    fun archive(
        key: String,
        archived: Boolean,
    ) {
        if (!roles.available() ||
            !channels.available()
        ) {
            return log.warn("[cohort] {} was archived or restored with no bot to move its channels", key)
        }
        val cohort = cohorts.findByDefinitionKey(key) ?: return
        val roleId = roleOf(requireNotNull(cohort.id)) ?: return
        val opened = channels.openedTo(roleId).filter { it.kind != KeptChannelKind.CATEGORY }.map { it.id }
        if (archived) channels.archive(opened) else channels.restore(opened)
    }

    // Where another cohort follows the role, it is refused, or taken from that cohort when the board asked to move it.
    private fun takeFromOthers(
        cohortId: Long,
        roleId: String,
        move: Boolean,
    ) {
        val owner = targets.findFirstBySystemAndExternalId(TargetSystem.DISCORD.name, roleId) ?: return
        if (owner.cohortId == cohortId) return
        if (!move) {
            val label = owner.cohortId?.let { cohorts.findById(it).map { cohort -> cohort.label }.orElse(null) } ?: owner.label
            throw TargetLinkedElsewhere(TargetSystem.DISCORD, roleId, label)
        }
        targets.delete(owner)
    }

    // A free role already called as the cohort is linked rather than made a second time.
    private fun roleFor(
        cohortId: Long,
        choice: DiscordChoice,
    ): String? =
        when {
            choice.roleId != null -> targeting.linkExisting(cohortId, TargetSystem.DISCORD, choice.roleId).externalId
            choice.createRole -> {
                val label = labelOf(cohortId)
                freeRoleNamed(label)?.let { targeting.linkExisting(cohortId, TargetSystem.DISCORD, it).externalId }
                    ?: targeting.create(cohortId, TargetSystem.DISCORD, label, null).externalId
            }
            else -> null
        }

    // A text channel already called so is opened to the role, so a second run makes nothing twice.
    private fun createOrOpen(
        name: String,
        category: String,
        roleId: String,
        held: Collection<String>,
    ) {
        val existing = channels.channels().firstOrNull { it.kind == KeptChannelKind.TEXT && plainName(it.name) == plainName(name) }
        when {
            existing == null -> channels.createPrivate(name, category, roleId)
            existing.id !in held -> channels.open(existing.id, roleId, private = true)
        }
    }

    private fun freeRoleNamed(label: String): String? {
        val linked = targets.findAllBySystem(TargetSystem.DISCORD.name).mapNotNull(targetIds::find).toSet()
        return roles.roles().firstOrNull { it.id !in linked && plainName(it.name) == plainName(label) }?.id
    }

    private fun isBoardCommittee(cohortId: Long): Boolean =
        cohorts
            .findById(cohortId)
            .map { it.type == CohortType.COMMITTEE_MEMBERS && it.label.equals(BOARD_COMMITTEE, ignoreCase = true) }
            .orElse(false)

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
        } catch (e: DiscordRefused) {
            throw TargetSystemRefused(TargetSystem.DISCORD, e.message.orEmpty()).apply { initCause(e) }
        }

    companion object {
        /**
         * The committee the board organises events through. The board in office holds the @Board role,
         * so its committee holds none, and adoption never matches it to one.
         */
        const val BOARD_COMMITTEE = "Board"

        private val log = LoggerFactory.getLogger(CohortDiscord::class.java)
    }
}
