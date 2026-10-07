package net.blueshell.api.committee.api

/**
 * Implemented by the module that holds events against a committee, so a deletion can count them
 * and hand them to another committee without this module learning what an event is.
 */
interface CommitteeEvents {
    /** How many live events [committeeId] organises. */
    fun countOf(committeeId: Long): Long

    /** Hands every event of [from], deleted ones included, to [to]. */
    fun handOver(
        from: Long,
        to: Long,
    )
}
