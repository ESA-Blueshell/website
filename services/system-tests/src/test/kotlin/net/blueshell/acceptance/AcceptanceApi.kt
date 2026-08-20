package net.blueshell.acceptance

import io.restassured.http.ContentType
import io.restassured.response.Response
import net.blueshell.systemtests.TestEnvironment
import net.blueshell.systemtests.TestHelper

// The only place that knows the flow is driven over HTTP, so swapping the driver
// touches this file and nothing in features/.
object AcceptanceApi {

    fun signIn(user: TestHelper.RegisteredUser): TestHelper.LoginCookies = TestHelper.login(user)

    /** Unlike signIn, does not fail the test on a rejection. */
    fun attemptSignIn(user: TestHelper.RegisteredUser): Response =
        TestHelper.givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"${user.username}","password":"${user.password}"}""")
            .`when`()
            .post("/auth")

    fun confirmEmailAddress(rawToken: String): Response =
        TestHelper.givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .contentType(ContentType.JSON)
            .body("""{"token":"$rawToken"}""")
            .`when`()
            .post("/recovery/user/activate")

    fun submitMembershipApplication(cookies: TestHelper.LoginCookies, accepted: Boolean = true): Response =
        TestHelper.givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .cookie(TestEnvironment.authCookieName, cookies.auth)
            .contentType(ContentType.JSON)
            .body("""{"conditionsAccepted":$accepted}""")
            .`when`()
            .post("/memberships")

    fun saveSignupAddress(signupToken: String, houseNumber: String = "5"): Response =
        TestHelper.givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .header(SIGNUP_TOKEN_HEADER, signupToken)
            .contentType(ContentType.JSON)
            .body(
                """
                {"country":"NL","city":"Enschede","street":"Drienerlolaan",
                 "houseNumber":"$houseNumber","zipCode":"7522NB"}
                """.trimIndent(),
            )
            .`when`()
            .post("/signup/address")

    fun submitSignupApplication(signupToken: String, accepted: Boolean = true): Response =
        TestHelper.givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .header(SIGNUP_TOKEN_HEADER, signupToken)
            .contentType(ContentType.JSON)
            .body("""{"conditionsAccepted":$accepted}""")
            .`when`()
            .post("/signup/apply")

    fun updateSignupDetails(signupToken: String, firstName: String, user: TestHelper.RegisteredUser): Response =
        TestHelper.givenCsrfApi()
            .baseUri(TestHelper.apiBaseUrl)
            .header(SIGNUP_TOKEN_HEADER, signupToken)
            .contentType("application/json")
            .body(
                mapOf(
                    "username" to user.username,
                    "initials" to "AC",
                    "firstName" to firstName,
                    "lastName" to "Applicant",
                    "discord" to user.discord,
                    "phoneNumber" to user.phoneNumber,
                    "newsletter" to false,
                    "photoConsent" to false,
                )
            )
            .`when`()
            .patch("/signup/details")

    fun resendConfirmation(username: String): Response =
        TestHelper.givenCsrfApi()
            .baseUri(TestHelper.apiBaseUrl)
            .`when`()
            .post("/recovery/user/activate/resend/{username}", username)

    fun correctSignupEmail(signupToken: String, email: String): Response =
        TestHelper.givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .header(SIGNUP_TOKEN_HEADER, signupToken)
            .contentType(ContentType.JSON)
            .body("""{"email":"$email"}""")
            .`when`()
            .patch("/signup/email")

    /** Used by the scope scenarios: every attempt outside the token's remit. */
    fun attemptWithSignupToken(signupToken: String, attempt: String, otherUserId: Long?): Response {
        val spec = TestHelper.givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .header(SIGNUP_TOKEN_HEADER, signupToken)
            .contentType(ContentType.JSON)
        return when (attempt) {
            "change the password on that account" ->
                spec.body("""{"token":"$signupToken","password":"Hijacked1!"}""")
                    .`when`().post("/recovery/password")
            "read that account's details back" ->
                spec.`when`().get("/users/$otherUserId")
            "submit an application through the signed-in route" ->
                spec.body("""{"conditionsAccepted":true}""").`when`().post("/memberships")
            "sign up for an event" ->
                spec.body("""{}""").`when`().post("/events/1/signups")
            else -> error("Unknown attempt \"$attempt\" — add it to AcceptanceApi.attemptWithSignupToken.")
        }
    }

    fun confirmationEmailCount(email: String): Int =
        TestHelper.findEmails(recipient = email, subject = CONFIRMATION_SUBJECT).size

    const val CONFIRMATION_SUBJECT: String = "Activate your Account"
    const val SIGNUP_TOKEN_HEADER: String = "X-Signup-Token"
}
