package net.blueshell.api.user.domain

/**
 * The two membership changes the user manager applies to a whole selection.
 *
 * Neither is anchored to a contribution period: a member leaves or returns on their own
 * schedule, which is why these live apart from the contribution bulk actions even though
 * both are driven from the same table.
 */
enum class BulkMembershipOperation {
    /** End every active membership, effective today. */
    END,
}
