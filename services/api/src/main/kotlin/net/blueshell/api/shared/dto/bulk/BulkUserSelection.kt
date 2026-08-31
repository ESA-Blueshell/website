package net.blueshell.api.shared.dto.bulk

/**
 * The ids in a bulk selection an action can still act on, and the violations for the rest.
 *
 * Every bulk action is driven from a table the client rendered earlier, so every one of
 * them has to answer the same two questions before it does anything: which of these ids
 * were never users, and which have since been deleted. Asking them in one place keeps the
 * reasons — and the sentences they carry — from drifting apart between domains.
 */
object BulkUserSelection {

    /**
     * Sorts a selection into the ids worth inspecting further and the ones already at
     * fault. A deleted user still resolves by id — deletion anonymises the account and
     * keeps the row for a restore window — so [isDeleted] is what tells them apart from
     * ids that were never users at all.
     *
     * Nothing is thrown here: an action with rules of its own inspects [usable] for them
     * and refuses once, with every reason at hand.
     */
    fun classify(
        userIds: List<Long>,
        exists: (Long) -> Boolean,
        isDeleted: (Long) -> Boolean,
    ): Classified {
        val unknown = userIds.filterNot(exists)
        val deleted = userIds.filterNot { it in unknown }.filter(isDeleted)

        val violations = buildList {
            if (unknown.isNotEmpty()) {
                add(
                    BulkSelectionRejected.Violation(
                        field = "userIds",
                        code = BulkSelectionRejected.UNKNOWN_USERS,
                        values = unknown,
                        message = "${unknown.size} of the selected users no longer exist.",
                    ),
                )
            }
            if (deleted.isNotEmpty()) {
                add(
                    BulkSelectionRejected.Violation(
                        field = "userIds",
                        code = BulkSelectionRejected.DELETED_USERS,
                        values = deleted,
                        message = "${deleted.size} of the selected users have been deleted.",
                    ),
                )
            }
        }
        return Classified(usable = userIds.filterNot { it in unknown || it in deleted }, violations = violations)
    }

    /**
     * @param usable the ids that resolve to a live user, which is all an action can judge
     *        its own rules against
     * @param violations why the others are at fault, empty when the whole selection is fine
     */
    data class Classified(
        val usable: List<Long>,
        val violations: List<BulkSelectionRejected.Violation>,
    )
}
