package net.blueshell.acceptance.steps

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import net.blueshell.acceptance.AcceptanceApi
import net.blueshell.acceptance.AcceptanceWorld
import net.blueshell.acceptance.Inbox
import net.blueshell.systemtests.TestEnvironment
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import java.time.Duration

class SignInLifetimeSteps(
    private val world: AcceptanceWorld,
) {
    private val cookies = mutableListOf<String>()

    private fun signInOnce(): String {
        val response = AcceptanceApi.attemptSignIn(world.applicant())
        assertThat(response.statusCode).describedAs(response.asString()).isEqualTo(200)
        return requireNotNull(response.cookie(TestEnvironment.authCookieName))
    }

    /** Uses the site with [cookie] and answers the cookie it rotated to, if it did. */
    private fun use(cookie: String): String {
        val response = AcceptanceApi.useTheSite(cookie)
        assertThat(response.statusCode).describedAs(response.asString()).isEqualTo(200)
        return response.cookie(TestEnvironment.authCookieName)?.takeIf { it.isNotBlank() } ?: cookie
    }

    @Given("a member who is signed in")
    fun aMemberWhoIsSignedIn() {
        world.rememberApplicant(TestHelper.registerAndActivate())
        cookies += signInOnce()
    }

    @Given("a member who is signed in in two browsers")
    fun aMemberWhoIsSignedInInTwoBrowsers() {
        aMemberWhoIsSignedIn()
        cookies += signInOnce()
    }

    @When("fifteen days pass without them using the site")
    fun fifteenDaysPass() {
        AcceptanceApi.advanceClock(Duration.ofDays(15).seconds)
    }

    @When("they use the site every day for thirty-one days")
    fun theyUseTheSiteEveryDay() {
        var cookie = cookies.single()
        repeat(29) {
            AcceptanceApi.advanceClock(Duration.ofDays(1).seconds)
            cookie = use(cookie)
        }
        AcceptanceApi.advanceClock(Duration.ofDays(2).seconds)
        cookies[0] = cookie
    }

    @When("they reset their password through the emailed link")
    fun theyResetTheirPassword() {
        AcceptanceApi.requestPasswordReset(world.applicant().username)
        val email = Inbox.await(world.applicant().email, "Reset Your Blueshell Account Password")
        val response = AcceptanceApi.setPassword(AcceptanceApi.linkToken(email.htmlContent, "account/reset-password"), "Another123!pass")
        assertThat(response.statusCode).describedAs(response.asString()).isEqualTo(204)
    }

    @When("they sign out everywhere else from the first")
    fun theySignOutEverywhereElse() {
        val response = AcceptanceApi.endOtherSignIns(cookies.first())
        assertThat(response.statusCode).describedAs(response.asString()).isEqualTo(204)
    }

    @Then("their sign-in is refused")
    fun theirSignInIsRefused() {
        assertThat(AcceptanceApi.useTheSite(cookies.single()).statusCode).isEqualTo(401)
    }

    @Then("the first browser is still signed in and the second is not")
    fun onlyTheFirstIsSignedIn() {
        val first = AcceptanceApi.useTheSite(cookies[0])
        assertThat(first.statusCode).isEqualTo(200)
        assertThat(first.jsonPath().getList<Any>("$")).hasSize(1)
        assertThat(AcceptanceApi.useTheSite(cookies[1]).statusCode).isEqualTo(401)
    }
}
