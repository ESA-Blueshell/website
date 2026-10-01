package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.discord.api.DiscordRoleKeeper
import net.blueshell.api.discord.api.KeptRole
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.user.api.UserService
import org.springframework.stereotype.Service

/**
 * Discord's [TargetStrategy]: a cohort's target is a role, and a person is on it through the Discord
 * account they linked to the site. Somebody with none is unreachable; the site never links one for
 * them. Without a bot in the server the strategy is not available, and nothing here is called.
 */
@Service
class DiscordTargetStrategy(
    private val roles: DiscordRoleKeeper,
    private val users: UserService,
) : TargetStrategy {
    override val descriptor = TargetDescriptor(system = TargetSystem.DISCORD, kind = TargetKind.ROLE)

    override fun available(): Boolean = roles.available()

    override fun catalog(query: String?): List<ExternalTarget> {
        if (!roles.available()) return emptyList()
        val wanted = query?.trim()?.lowercase().orEmpty()
        return roles
            .roles()
            .filter { wanted.isEmpty() || it.id == wanted || it.name.lowercase().contains(wanted) }
            .map(::targetOf)
    }

    override fun resolve(externalId: String): ExternalTarget? = if (roles.available()) roles.role(externalId)?.let(::targetOf) else null

    override fun members(external: ExternalTarget): List<ExternalMember> =
        roles.holders(external.externalId).map { ExternalMember(it.discordUserId, it.name) }

    override fun memberIds(userIds: Set<Long>): Map<Long, String> =
        users
            .findAllByIds(userIds)
            .mapNotNull { user -> user.discordId?.takeIf { it.isNotBlank() }?.let { requireNotNull(user.id) to it } }
            .toMap()

    override fun ownersOf(externalUserIds: Set<String>): Map<String, Long> =
        users.findAllByDiscordIds(externalUserIds).mapNotNull { user -> user.discordId?.let { it to requireNotNull(user.id) } }.toMap()

    override fun add(
        external: ExternalTarget,
        externalUserId: String,
    ) = roles.add(external.externalId, externalUserId)

    override fun remove(
        external: ExternalTarget,
        externalUserId: String,
    ) = roles.remove(external.externalId, externalUserId)

    // A role has no folder; what it opens is set on the channels it is given.
    override fun create(
        label: String,
        folder: String?,
    ): ExternalTarget = targetOf(roles.create(label))

    override fun rename(
        external: ExternalTarget,
        name: String,
    ): ExternalTarget = targetOf(roles.rename(external.externalId, name))

    override fun createFolder(name: String): List<String> = throw UnsupportedOperationException("Discord roles sit in no folder")

    override fun delete(external: ExternalTarget) = roles.delete(external.externalId)

    private fun targetOf(role: KeptRole) =
        ExternalTarget(system, role.id, descriptor.kind, role.name, path = listOf(TargetSystem.DISCORD.shownName))
}
