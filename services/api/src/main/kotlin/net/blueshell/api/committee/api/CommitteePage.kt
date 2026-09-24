package net.blueshell.api.committee.api

/**
 * What the board sets about a committee beside its name, description and members.
 *
 * [address] null makes one from the name; [banner] is where a stored picture is, or nothing for
 * none; [gameCodes] null leaves the games as they are.
 */
data class CommitteePage(
    val address: String? = null,
    val listed: Boolean = true,
    val banner: String? = null,
    val gameCodes: List<String>? = null,
)
