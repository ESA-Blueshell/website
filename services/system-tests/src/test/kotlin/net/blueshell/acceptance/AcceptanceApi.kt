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

    fun submitMembershipApplication(cookies: TestHelper.LoginCookies): Response =
        TestHelper.givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .cookie(TestEnvironment.authCookieName, cookies.auth)
            .contentType(ContentType.JSON)
            .`when`()
            .post("/memberships")

    fun confirmationEmailCount(email: String): Int =
        TestHelper.findEmails(recipient = email, subject = CONFIRMATION_SUBJECT).size

    const val CONFIRMATION_SUBJECT: String = "Activate your Account"
}
