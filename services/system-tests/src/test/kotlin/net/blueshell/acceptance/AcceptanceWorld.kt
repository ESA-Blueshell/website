package net.blueshell.acceptance

import net.blueshell.systemtests.TestHelper

// Constructed per scenario by picocontainer, so step classes hold no static state
// and scenarios cannot leak into each other.
class AcceptanceWorld {

    private var applicant: TestHelper.RegisteredUser? = null

    /** Erased by the @After hook. */
    val createdUsernames: MutableList<String> = mutableListOf()

    /** Raw continuation token from POST /signup, for the token-scoped steps. */
    var signupToken: String? = null

    /** Set when a scenario corrects the address, so the Then can name it. */
    var correctedEmail: String? = null

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

    fun signupTokenOrFail(): String =
        signupToken ?: error("This scenario has no signup session — begin a signup first.")

    fun lastStatusCodeOrFail(): Int =
        lastStatusCode ?: error("No request has been made in this scenario yet.")
}
