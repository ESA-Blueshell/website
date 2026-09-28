package net.blueshell.api.user.api

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * An application for membership accepts the conditions, on the signup route and the signed-in one
 * alike. `conditionsAcceptedAt` records that acceptance, so a use case stamps it only once
 * [requireAccepted] has passed.
 * TWIN: MembershipForm's `accepted` rule, which asks the same of the checkbox on the page.
 */
object MembershipConditions {
    const val NOT_ACCEPTED_MESSAGE = "The membership conditions must be accepted"

    /** The request's validation refuses first; this holds for a caller that skips it. */
    fun requireAccepted(accepted: Boolean) {
        if (!accepted) throw ResponseStatusException(HttpStatus.BAD_REQUEST, NOT_ACCEPTED_MESSAGE)
    }
}
