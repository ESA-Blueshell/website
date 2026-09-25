package net.blueshell.acceptance

import io.restassured.http.ContentType
import io.restassured.response.Response
import net.blueshell.systemtests.TestEnvironment
import net.blueshell.systemtests.TestHelper
import net.blueshell.systemtests.TotpCodes
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

// The only place that knows the flow is driven over HTTP, so swapping the driver
// touches this file and nothing in features/.
object AcceptanceApi {
    fun signIn(user: TestHelper.RegisteredUser): TestHelper.LoginCookies = TestHelper.login(user)

    /** Unlike signIn, does not fail the test on a rejection. */
    fun attemptSignIn(user: TestHelper.RegisteredUser): Response =
        TestHelper
            .givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .contentType(ContentType.JSON)
            .body("""{"username":"${user.username}","password":"${user.password}"}""")
            .`when`()
            .post("/auth")

    fun confirmEmailAddress(rawToken: String): Response =
        TestHelper
            .givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .contentType(ContentType.JSON)
            .body("""{"token":"$rawToken"}""")
            .`when`()
            .post("/recovery/user/activate")

    fun submitMembershipApplication(
        cookies: TestHelper.LoginCookies,
        accepted: Boolean = true,
    ): Response =
        TestHelper
            .givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .cookie(TestEnvironment.authCookieName, cookies.auth)
            .contentType(ContentType.JSON)
            .body("""{"conditionsAccepted":$accepted}""")
            .`when`()
            .post("/memberships")

    fun saveSignupAddress(
        signupToken: String,
        houseNumber: String = "5",
    ): Response =
        TestHelper
            .givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .header(SIGNUP_TOKEN_HEADER, signupToken)
            .contentType(ContentType.JSON)
            .body(
                """
                {"country":"NL","city":"Enschede","street":"Drienerlolaan",
                 "houseNumber":"$houseNumber","zipCode":"7522NB"}
                """.trimIndent(),
            ).`when`()
            .post("/signup/address")

    fun submitSignupApplication(
        signupToken: String,
        accepted: Boolean = true,
    ): Response =
        TestHelper
            .givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .header(SIGNUP_TOKEN_HEADER, signupToken)
            .contentType(ContentType.JSON)
            .body("""{"conditionsAccepted":$accepted}""")
            .`when`()
            .post("/signup/apply")

    fun updateSignupDetails(
        signupToken: String,
        firstName: String,
        user: TestHelper.RegisteredUser,
    ): Response =
        TestHelper
            .givenCsrfApi()
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
                ),
            ).`when`()
            .patch("/signup/details")

    fun resendConfirmation(username: String): Response =
        TestHelper
            .givenCsrfApi()
            .baseUri(TestHelper.apiBaseUrl)
            .`when`()
            .post("/recovery/user/activate/resend/{username}", username)

    fun correctSignupEmail(
        signupToken: String,
        email: String,
    ): Response =
        TestHelper
            .givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .header(SIGNUP_TOKEN_HEADER, signupToken)
            .contentType(ContentType.JSON)
            .body("""{"email":"$email"}""")
            .`when`()
            .patch("/signup/email")

    /** Used by the scope scenarios: every attempt outside the token's remit. */
    fun attemptWithSignupToken(
        signupToken: String,
        attempt: String,
        otherUserId: Long?,
    ): Response {
        val spec =
            TestHelper
                .givenCsrfApi()
                .baseUri(TestEnvironment.apiUrl)
                .header(SIGNUP_TOKEN_HEADER, signupToken)
                .contentType(ContentType.JSON)
        return when (attempt) {
            "change the password on that account" ->
                spec
                    .body("""{"token":"$signupToken","password":"Hijacked1!"}""")
                    .`when`()
                    .post("/recovery/password")
            "read that account's details back" ->
                spec.`when`().get("/users/$otherUserId")
            "submit an application through the signed-in route" ->
                spec.body("""{"conditionsAccepted":true}""").`when`().post("/memberships")
            "sign up for an event" ->
                spec.body("""{}""").`when`().post("/events/1/signups")
            else -> error("Unknown attempt \"$attempt\" — add it to AcceptanceApi.attemptWithSignupToken.")
        }
    }

    fun confirmationEmailCount(email: String): Int = TestHelper.findEmails(recipient = email, subject = CONFIRMATION_SUBJECT).size

    const val CONFIRMATION_SUBJECT: String = "Activate your Account"
    const val SIGNUP_TOKEN_HEADER: String = "X-Signup-Token"

    /** Sets up an authenticator app from a sign-in the way the security page does; answers its key. */
    fun setUpTwoFactor(user: TestHelper.RegisteredUser): String = setUpTwoFactorWithBackupCodes(user).first

    /** Like [setUpTwoFactor], and answers the ten backup codes too. */
    fun setUpTwoFactorWithBackupCodes(user: TestHelper.RegisteredUser): Pair<String, List<String>> {
        val cookies = TestHelper.login(user)
        val setUp = authed(cookies.auth).body("""{"password":"${user.password}"}""").post("/users/me/two-factor/setup")
        require(setUp.statusCode == 200) { "set-up refused: ${setUp.statusCode} ${setUp.asString()}" }
        val key = setUp.jsonPath().getString("key")
        val confirm = authed(cookies.auth).body("""{"code":"${TotpCodes.now(key)}"}""").post("/users/me/two-factor/confirm")
        require(confirm.statusCode == 200) { "confirm refused: ${confirm.statusCode} ${confirm.asString()}" }
        val saved = authed(cookies.auth).post("/users/me/two-factor/saved")
        require(saved.statusCode == 204) { "saving refused: ${saved.statusCode} ${saved.asString()}" }
        return key to confirm.jsonPath().getList("codes", String::class.java)
    }

    /**
     * Sets up an authenticator app on [authCookie] without the password, the way the set-up a
     * granted role is sent to at sign-in does; answers the status of each of the three steps.
     */
    fun setUpTwoFactorWithoutPassword(authCookie: String): List<Int> {
        val setUp = authed(authCookie).body("{}").post("/users/me/two-factor/setup")
        if (setUp.statusCode != 200) return listOf(setUp.statusCode)
        val key = setUp.jsonPath().getString("key")
        val confirm = authed(authCookie).body("""{"code":"${TotpCodes.now(key)}"}""").post("/users/me/two-factor/confirm")
        val saved = authed(authCookie).post("/users/me/two-factor/saved")
        return listOf(setUp.statusCode, confirm.statusCode, saved.statusCode)
    }

    fun grantRoles(
        authCookie: String?,
        userId: Long,
        vararg roles: String,
    ): Response = authed(authCookie.orEmpty()).body("""{"roles":[${roles.joinToString { "\"$it\"" }}]}""").put("/users/$userId/roles")

    /** The code step for the challenge [passwordStep] opened. */
    fun answerChallenge(
        passwordStep: Response,
        code: String,
        trustThisBrowser: Boolean = false,
    ): Response =
        TestHelper
            .givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .cookie(CHALLENGE_COOKIE, passwordStep.cookie(CHALLENGE_COOKIE).orEmpty())
            .contentType(ContentType.JSON)
            .body("""{"code":"$code","trustThisBrowser":$trustThisBrowser}""")
            .`when`()
            .post("/auth/two-factor")

    /** The password step from a browser that holds [trustedBrowser]. */
    fun attemptSignInFrom(
        user: TestHelper.RegisteredUser,
        trustedBrowser: String,
    ): Response =
        TestHelper
            .givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .cookie(TRUSTED_BROWSER_COOKIE, trustedBrowser)
            .contentType(ContentType.JSON)
            .body("""{"username":"${user.username}","password":"${user.password}"}""")
            .`when`()
            .post("/auth")

    /** Uses the site once with [authCookie], and answers the cookie to carry on with. */
    fun useTheSite(authCookie: String): Response =
        TestHelper
            .givenApi()
            .baseUri(TestEnvironment.apiUrl)
            .cookie(TestEnvironment.authCookieName, authCookie)
            .`when`()
            .get("/users/me/sign-ins")

    fun endOtherSignIns(authCookie: String): Response = authed(authCookie).delete("/users/me/sign-ins/others")

    fun requestEmailChange(
        authCookie: String,
        email: String,
    ): Response = authed(authCookie).body("""{"email":"$email"}""").post("/users/me/email")

    fun confirmEmailChange(token: String): Response =
        TestHelper
            .givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .contentType(ContentType.JSON)
            .body("""{"token":"$token"}""")
            .`when`()
            .post("/recovery/email/confirm")

    fun requestPasswordReset(username: String): Response =
        TestHelper
            .givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .`when`()
            .post("/recovery/password/reset/$username")

    fun setPassword(
        token: String,
        password: String,
    ): Response =
        TestHelper
            .givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .contentType(ContentType.JSON)
            .body("""{"token":"$token","password":"$password"}""")
            .`when`()
            .post("/recovery/password")

    fun advanceClock(seconds: Long): Response =
        TestHelper.givenCsrfApi().baseUri(TestEnvironment.apiUrl).queryParam("seconds", seconds).`when`().post(
            "/test-support/clock/advance",
        )

    fun resetClock(): Response =
        TestHelper
            .givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .`when`()
            .delete("/test-support/clock")

    fun listUsers(authCookie: String?): Response =
        TestHelper
            .givenApi()
            .baseUri(TestEnvironment.apiUrl)
            .cookie(TestEnvironment.authCookieName, authCookie.orEmpty())
            .`when`()
            .get("/users")

    fun resetTwoFactor(
        authCookie: String?,
        userId: Long,
        reason: String,
    ): Response = authed(authCookie.orEmpty()).body("""{"reason":"$reason"}""").post("/users/$userId/two-factor/reset")

    fun unlock(
        authCookie: String,
        userId: Long,
        reason: String,
    ): Response = authed(authCookie).body("""{"reason":"$reason"}""").post("/users/$userId/unlock")

    fun changePassword(
        user: TestHelper.RegisteredUser,
        newPassword: String,
    ): Response =
        authed(TestHelper.login(user).auth)
            .body("""{"currentPassword":"${user.password}","newPassword":"$newPassword"}""")
            .put("/users/me/password")

    fun followLockLink(token: String): Response =
        TestHelper
            .givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .contentType(ContentType.JSON)
            .body("""{"token":"$token"}""")
            .`when`()
            .post("/recovery/lock")

    fun reenrol(
        user: TestHelper.RegisteredUser,
        token: String,
    ): Response =
        TestHelper
            .givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .contentType(ContentType.JSON)
            .body("""{"token":"$token","username":"${user.username}","password":"${user.password}"}""")
            .`when`()
            .post("/recovery/two-factor/re-enrol")

    /** The token a link to [path] in an email carries, as the page it opens would read it. */
    fun linkToken(
        html: String,
        path: String,
    ): String {
        val encoded =
            Regex("""${Regex.escape(path)}#token=([^"'&<\s)]+)""").find(html)?.groupValues?.get(1)
                ?: error("No link to $path in that email")
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8)
    }

    private fun authed(authCookie: String) =
        TestHelper
            .givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .cookie(TestEnvironment.authCookieName, authCookie)
            .contentType(ContentType.JSON)
            .`when`()

    private const val CHALLENGE_COOKIE = "BSH_2FA_CHALLENGE"
    const val TRUSTED_BROWSER_COOKIE = "BSH_TRUSTED_BROWSER"
}
