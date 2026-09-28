package net.blueshell.api.esports.api

import net.blueshell.api.shared.enums.TeamRole

/**
 * What a roster write says about a person on a line-up, whether it adds them or edits them.
 * Where they stand and whose account they are is said separately.
 */
data class RosterEntryInput(
    val handle: String,
    val role: TeamRole,
    val displayName: String? = null,
    val roleTitle: String? = null,
    val description: String? = null,
    val icon: String? = null,
)
