package net.blueshell.api.cohort.domain

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordRoleKeeper
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
 * open, and [createChannel] names a new private channel to make for it.
 */
data class DiscordChoice(
    val roleId: String? = null,
    val createRole: Boolean = false,
    val channelIds: List<String> = emptyList(),
    val createChannel: String? = null,
)

/**
 * A cohort's role and private channels, as a committee's or a team's form sets them. The role is the cohort's
 * Discord target, so it follows the cohort's people; a channel is opened to that role by an overwrite
 * and kept from everybody else, under the category the caller names when the site makes it.
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
    private val definitions: CohortDefinitionRegistry,
    private val updater: CohortMembershipUpdater,
) {
    fun read(key: String): DiscordPlace {
        if (!roles.available() || !channels.available()) return DiscordPlace(false, null, null, emptyList())
        val roleId = roleOf(cohortIdOf(key))
        return refusedBy(TargetSystem.DISCORD) {
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
        val linked = roleOf(cohortId)
        if (linked == null && choice.roleId != null) {
            refuseIfFollowed(cohortId, choice.roleId)
            refuseUnless(handable = choice.roleId)
        }
        val roleId = linked ?: refusedBy(TargetSystem.DISCORD) { roleFor(cohortId, choice) } ?: return read(key)
        // A role the cohort did not have yet is given to its people now, not as each of them changes.
        if (linked == null) definitions.byKey(key)?.let(updater::updateCohort)
        refusedBy(TargetSystem.DISCORD) {
            val opened = channels.openedTo(roleId)
            val toOpen = choice.channelIds - opened.map { it.id }.toSet()
            val inCategory = channelsUnder(category)
            toOpen.firstOrNull { id -> inCategory.none { it.id == id } }?.let { id ->
                throw ChannelOutsideCategory(channels.channels().firstOrNull { it.id == id }?.name ?: id, category)
            }
            // An existing channel is let in to the role and stays open to whoever it was open to.
            toOpen.forEach { channels.open(it, roleId, private = false) }
            // The form showed a linked role's channels, not those of a role it is linking now; a
            // category is opened from elsewhere and never named on a form.
            if (linked != null) {
                opened
                    .filter { it.kind != KeptChannelKind.CATEGORY && it.id !in choice.channelIds }
                    .forEach { channels.close(it.id, roleId) }
            }
            choice.createChannel
                ?.trim()
                ?.removePrefix("#")
                ?.takeIf { it.isNotEmpty() }
                ?.let { createOrOpen(it, category, inCategory, roleId, held = choice.channelIds) }
        }
        return read(key)
    }

    /**
     * Moves the private channels under [category] that only the cohort's role is let into to the
     * archive category, or back out. A shared or public channel stays where the others find it. Without a bot, or
     * where Discord refuses, nothing moves and the log says so rather than failing the archive.
     */
    fun archive(
        key: String,
        archived: Boolean,
        category: String,
    ) {
        if (!roles.available() || !channels.available()) return log.warn("[cohort] {} was archived or restored with no bot", key)
        val cohort = cohorts.findByDefinitionKey(key) ?: return
        val roleId = roleOf(requireNotNull(cohort.id)) ?: return
        // After the commit, so a failure here is said in the log rather than failing the archive.
        runCatching {
            val own = channels.openings().filter { it.private && it.roleIds == listOf(roleId) }.map { it.channel }
            if (archived) {
                val inCategory = channelsUnder(category).map { it.id }.toSet()
                channels.archive(own.filter { it.id in inCategory }.map { it.id })
            } else {
                channels.restore(own.map { it.id })
            }
        }.onFailure { log.warn("[cohort] {}'s channels could not be moved: {}", key, it.message) }
    }

    private fun refuseIfFollowed(
        cohortId: Long,
        roleId: String,
    ) {
        val owner = targets.findFirstBySystemAndExternalId(TargetSystem.DISCORD.name, roleId) ?: return
        if (owner.cohortId == cohortId) return
        val label = owner.cohortId?.let { cohorts.findById(it).map { cohort -> cohort.label }.orElse(null) } ?: owner.label
        throw TargetLinkedElsewhere(TargetSystem.DISCORD, roleId, label)
    }

    // The board fills a committee's seats, so a channel anywhere else would let it open any room to anybody.
    private fun channelsUnder(category: String): List<KeptChannel> =
        channels.channels().filter { it.kind != KeptChannelKind.CATEGORY && it.category.equals(category, ignoreCase = true) }

    // Only a role the site keeps, which carries no powers over the server, and the bot can hand out.
    private fun refuseUnless(handable: String) {
        val role = refusedBy(TargetSystem.DISCORD) { roles.role(handable) } ?: throw TargetNotFound(TargetSystem.DISCORD, handable)
        if (!role.assignable) {
            throw TargetSystemRefused(TargetSystem.DISCORD, "The bot's role is below ${role.name}, so it cannot hand it out.")
        }
    }

    // A free role already called as the cohort is linked rather than made a second time.
    private fun roleFor(
        cohortId: Long,
        choice: DiscordChoice,
    ): String? =
        when {
            choice.roleId != null -> targeting.linkExisting(cohortId, TargetSystem.DISCORD, choice.roleId).externalId
            choice.createRole -> {
                val label = cohorts.findById(cohortId).map { it.label }.orElseThrow()
                freeRoleNamed(label)?.let { targeting.linkExisting(cohortId, TargetSystem.DISCORD, it).externalId }
                    ?: targeting.create(cohortId, TargetSystem.DISCORD, label, null).externalId
            }
            else -> null
        }

    // A text channel already called so is opened to the role, so a second run makes nothing twice.
    private fun createOrOpen(
        name: String,
        category: String,
        inCategory: List<KeptChannel>,
        roleId: String,
        held: Collection<String>,
    ) {
        val existing = inCategory.firstOrNull { it.kind == KeptChannelKind.TEXT && plainName(it.name) == plainName(name) }
        when {
            existing == null -> channels.createPrivate(name, category, roleId)
            existing.id !in held -> channels.open(existing.id, roleId, private = false)
        }
    }

    private fun freeRoleNamed(label: String): String? {
        val linked = targets.findAllBySystem(TargetSystem.DISCORD.name).mapNotNull(targetIds::find).toSet()
        return roles.roles().firstOrNull { it.assignable && it.id !in linked && plainName(it.name) == plainName(label) }?.id
    }

    private fun isBoardCommittee(cohortId: Long): Boolean =
        cohorts
            .findById(cohortId)
            .map { it.type == CohortType.COMMITTEE_MEMBERS && it.label.equals(BOARD_COMMITTEE, ignoreCase = true) }
            .orElse(false)

    // A committee added a moment ago may not have its cohort yet, so one is registered for it.
    private fun cohortIdOf(key: String): Long {
        val cohort =
            cohorts.findByDefinitionKey(key)
                ?: registrar.register().let { cohorts.findByDefinitionKey(key) }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No cohort is defined as $key")
        return requireNotNull(cohort.id)
    }

    private fun roleOf(cohortId: Long): String? = targets.findByCohortIdAndSystem(cohortId, TargetSystem.DISCORD.name)?.let(targetIds::find)

    companion object {
        private val log = LoggerFactory.getLogger(CohortDiscord::class.java)

        /**
         * The committee the board organises events through. The board in office will hold its own
         * role, so its committee holds none. TWIN: `BOARD_COMMITTEE` in `CommitteeEditor.vue`.
         */
        const val BOARD_COMMITTEE = "Board"
    }
}
