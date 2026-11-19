package net.blueshell.api.vote.web

import java.time.Instant

/** The December a vote integration test runs its vote in: closing at 23:59 in Amsterdam. */
internal object VoteDates {
    val OPENS: Instant = Instant.parse("2026-12-01T00:00:00Z")
    val CLOSES: Instant = Instant.parse("2026-12-21T22:59:00Z")
    val REVEAL: Instant = Instant.parse("2026-12-28T19:00:00Z")

    /** The three dates as the members of a create or reschedule request. */
    val JSON = """"opensAt":"$OPENS","closesAt":"$CLOSES","revealAt":"$REVEAL""""
}
