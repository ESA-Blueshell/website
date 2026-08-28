package net.blueshell.api.user.domain

import java.time.LocalDate

/**
 * Exposes the fields [MembershipValidator] needs from a command so the
 * membership interval invariants can be validated uniformly across create,
 * board-create and correct commands.
 */
interface MembershipIntervalCandidate {
    val candidateUserId: Long?

    /** Id of the membership being edited, or null when creating a new one. */
    val candidateMembershipId: Long?

    val candidateStartDate: LocalDate?

    val candidateEndDate: LocalDate?
}
