package net.blueshell.api.committee.domain

import java.time.Instant

/**
 * Time window of a single (user, committee) membership row, including
 * soft-deleted rows so callers can reason about historical membership.
 * `leftAt` carries the soft-delete sentinel `9999-12-31 23:59:59` for
 * currently-active memberships.
 */
data class CommitteeMembershipWindow(
    val committeeId: Long,
    val joinedAt: Instant,
    val leftAt: Instant,
)
