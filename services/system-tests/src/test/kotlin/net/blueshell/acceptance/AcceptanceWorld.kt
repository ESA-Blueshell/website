package net.blueshell.acceptance

import net.blueshell.systemtests.TestHelper

// Constructed per scenario by picocontainer, so step classes hold no static state
// and scenarios cannot leak into each other.
class AcceptanceWorld {

    private var applicant: TestHelper.RegisteredUser? = null

    /** Erased by the @After hook. */
    val createdUsernames: MutableList<String> = mutableListOf()

    var lastStatusCode: Int? = null

    var lastResponseBody: String? = null

    // Registering always sends one, so "no further mail" needs a baseline.
    var confirmationEmailsBeforeAction: Int? = null

    fun rememberApplicant(user: TestHelper.RegisteredUser) {
        applicant = user
        createdUsernames += user.username
    }

    fun applicant(): TestHelper.RegisteredUser =
        applicant ?: error("This scenario has no applicant — start it with a Given that establishes one.")

    fun applicantId(): Long =
        TestHelper.findUser(applicant().username)?.id
            ?: error("Applicant ${applicant().username} is not in the database")

    fun recordResponse(statusCode: Int, body: String?) {
        lastStatusCode = statusCode
        lastResponseBody = body
    }

    fun lastStatusCodeOrFail(): Int =
        lastStatusCode ?: error("No request has been made in this scenario yet.")
}
