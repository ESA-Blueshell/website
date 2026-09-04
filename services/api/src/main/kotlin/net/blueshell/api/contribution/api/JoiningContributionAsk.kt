package net.blueshell.api.contribution.api

import java.time.LocalDate

/**
 * The ask made of a member the moment they join.
 *
 * A port so `auth` can make it without reaching into this module: `auth` decides when a
 * signup is complete, `contribution` decides what a new member owes and what they are told.
 */
interface JoiningContributionAsk {
    /**
     * Ask [userId] for the contribution their membership starting on [membershipStartDate]
     * incurs, recording the ask and queueing the email.
     *
     * [membershipStartDate] both prices the ask and starts the clock: what is owed follows from
     * where it falls against the period's cutoff, and the payment window is counted from it
     * rather than from whenever this runs. Does nothing when there is no period to price against.
     */
    fun askOnJoining(userId: Long, membershipStartDate: LocalDate)
}
