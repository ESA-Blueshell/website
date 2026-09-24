package net.blueshell.acceptance.steps

import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.restassured.response.Response
import net.blueshell.acceptance.AcceptanceApi
import net.blueshell.acceptance.AcceptanceWorld
import net.blueshell.acceptance.Inbox
import net.blueshell.systemtests.TestEnvironment
import net.blueshell.systemtests.TestHelper
import net.blueshell.systemtests.TotpCodes
import org.assertj.core.api.Assertions.assertThat

class TwoFactorSteps(
    private val world: AcceptanceWorld,
) {
    private var key: String? = null
    private var lastCode: String? = null
    private var challenge: Response? = null

    private fun keyOrFail() = key ?: error("Nobody in this scenario has set up two-factor.")

    @Given("a member who has set up two-factor")
    fun aMemberWhoHasSetUpTwoFactor() {
        val member = TestHelper.registerAndActivate()
        world.rememberApplicant(member)
        key = AcceptanceApi.setUpTwoFactor(member)
        TotpCodes.awaitNextStep()
    }

    @Given("a board member who has not set up two-factor")
    fun aBoardMemberWhoHasNotSetUpTwoFactor() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        TestHelper.withoutTwoFactor(board.username)
        world.rememberApplicant(board)
    }

    @When("they sign in with their password")
    fun theySignInWithTheirPassword() {
        challenge = AcceptanceApi.attemptSignIn(world.applicant())
        world.recordResponse(challenge!!.statusCode, challenge!!.asString())
    }

    @When("they sign in with their password and a code from their authenticator app")
    @Given("they have signed in with a code")
    fun theySignInWithACode() {
        val code = TotpCodes.now(keyOrFail())
        lastCode = code
        val answer = AcceptanceApi.answerChallenge(AcceptanceApi.attemptSignIn(world.applicant()), code)
        world.recordResponse(answer.statusCode, answer.asString())
        answer.cookie(TestEnvironment.authCookieName)?.let { world.authCookies = TestHelper.LoginCookies(it, null) }
    }

    @When("they sign in again with that same code")
    fun theySignInAgainWithThatSameCode() {
        val answer = AcceptanceApi.answerChallenge(AcceptanceApi.attemptSignIn(world.applicant()), lastCode!!)
        world.recordResponse(answer.statusCode, answer.asString())
    }

    @Then("they are asked for a code")
    fun theyAreAskedForACode() {
        assertThat(challenge!!.jsonPath().getString("status")).isEqualTo("TWO_FACTOR_REQUIRED")
    }

    @And("no sign-in has been opened")
    fun noSignInHasBeenOpened() {
        assertThat(challenge!!.cookie(TestEnvironment.authCookieName).orEmpty()).isEmpty()
    }

    @Then("they are signed in with two-factor")
    fun theyAreSignedInWithTwoFactor() {
        assertThat(world.lastStatusCodeOrFail()).describedAs(world.lastResponseBody).isEqualTo(200)
        assertThat(world.lastResponseBody).contains("\"on\":true")
        assertThat(world.authCookiesOrFail().auth).isNotBlank()
    }

    @Then("the code is refused")
    fun theCodeIsRefused() {
        assertThat(world.lastStatusCodeOrFail()).isGreaterThanOrEqualTo(400)
        assertThat(world.lastResponseBody).contains("WrongCode")
    }

    @And("they are told to set up two-factor first")
    fun theyAreToldToSetUpTwoFactorFirst() {
        assertThat(world.lastResponseBody).contains("\"required\":true")
    }

    @And("the board's pages refuse them")
    fun theBoardsPagesRefuseThem() {
        val cookie = AcceptanceApi.attemptSignIn(world.applicant()).cookie(TestEnvironment.authCookieName)
        assertThat(AcceptanceApi.listUsers(cookie).statusCode).isGreaterThanOrEqualTo(400)
    }

    @When("an admin resets their two-factor")
    fun anAdminResetsTheirTwoFactor() {
        val admin = TestHelper.registerAndActivate()
        world.createdUsernames += admin.username
        val adminKey = AcceptanceApi.setUpTwoFactor(admin)
        TestHelper.grantRole(admin.username, "ADMIN")
        TotpCodes.awaitNextStep()
        val signedIn = AcceptanceApi.answerChallenge(AcceptanceApi.attemptSignIn(admin), TotpCodes.now(adminKey))
        val cookie = signedIn.cookie(TestEnvironment.authCookieName)
        val response = AcceptanceApi.resetTwoFactor(cookie, world.applicantId(), "lost the phone and the codes")
        world.recordResponse(response.statusCode, response.asString())
    }

    @Then("they receive a re-enrolment link")
    fun theyReceiveAReenrolmentLink() {
        Inbox.await(world.applicant().email, "set up two-factor again", world.lastStatusCode, world.lastResponseBody)
    }

    @And("signing in with their password alone is refused")
    fun signingInWithTheirPasswordAloneIsRefused() {
        val response = AcceptanceApi.attemptSignIn(world.applicant())
        assertThat(response.statusCode).isGreaterThanOrEqualTo(400)
        assertThat(response.asString()).contains("ReenrolmentRequired")
    }

    @And("signing in with their password and the re-enrolment link works")
    fun signingInWithTheirPasswordAndTheReenrolmentLinkWorks() {
        val email = Inbox.await(world.applicant().email, "set up two-factor again")
        val token = AcceptanceApi.linkToken(email.htmlContent, "account/re-enrol")
        val response = AcceptanceApi.reenrol(world.applicant(), token)
        assertThat(response.statusCode).describedAs(response.asString()).isEqualTo(200)
    }
}
