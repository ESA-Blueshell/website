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
     * [membershipStartDate] both prices the ask and starts the clock on it: what is owed
     * follows from where the joining date falls against the period's cutoff, and the payment
     * window is counted from that date rather than from whenever this runs.
     *
     * Does nothing when there is no contribution period to price against, which is the only
     * way this has nothing to say.
     */
    fun askOnJoining(userId: Long, membershipStartDate: LocalDate)
}
