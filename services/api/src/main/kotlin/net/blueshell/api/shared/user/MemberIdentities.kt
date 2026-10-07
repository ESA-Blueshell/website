package net.blueshell.api.shared.user

/**
 * A member's public-facing identity: the site [username], and the Discord account [discordId] they
 * linked if any. Never a real or legal name.
 */
data class MemberIdentity(
    val username: String,
    val discordId: String?,
)

/**
 * The public identity of accounts by their id. The user module knows; a page that shows members
 * asks, and neither depends on the other. An id with no account is simply absent from the result.
 */
fun interface MemberIdentities {
    fun of(ids: Collection<Long>): Map<Long, MemberIdentity>
}
