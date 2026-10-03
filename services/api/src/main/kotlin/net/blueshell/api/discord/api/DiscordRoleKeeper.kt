package net.blueshell.api.discord.api

/** A role in the server as another module keeps it. [assignable] is false for one the bot cannot hand out. */
data class KeptRole(
    val id: String,
    val name: String,
    val assignable: Boolean,
)

/** Somebody holding a role, by their Discord id and the name the server shows for them. */
data class RoleHolder(
    val discordUserId: String,
    val name: String,
)

/** Discord no longer has the role, or the bot cannot reach the server right now. */
class DiscordUnavailable(
    message: String,
) : RuntimeException(message)

/**
 * The server's roles, as the site keeps some of them in step: listing, holding, making and naming them.
 * Every call needs the bot; [available] says whether there is one in the server now.
 */
interface DiscordRoleKeeper {
    fun available(): Boolean

    /** Every role the site could keep: not @everyone, not an integration's, not the claim bot's. */
    fun roles(): List<KeptRole>

    fun role(id: String): KeptRole?

    /** Everybody holding [roleId] now, read from Discord rather than kept. */
    fun holders(roleId: String): List<RoleHolder>

    fun add(
        roleId: String,
        discordUserId: String,
    )

    fun remove(
        roleId: String,
        discordUserId: String,
    )

    fun create(name: String): KeptRole

    fun rename(
        roleId: String,
        name: String,
    ): KeptRole

    fun delete(roleId: String)
}
