package net.blueshell.acceptance.steps

import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import net.blueshell.acceptance.AcceptanceApi
import net.blueshell.acceptance.AcceptanceWorld
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat

class SignInSteps(private val world: AcceptanceWorld) {

    @When("they sign in")
    fun theySignIn() {
        val response = AcceptanceApi.attemptSignIn(world.applicant())
        world.recordResponse(response.statusCode, response.asString())
    }

    @When("they sign in with the wrong password")
    fun theySignInWithTheWrongPassword() {
        val applicant = world.applicant()
        val response = AcceptanceApi.attemptSignIn(applicant.copy(password = "${applicant.password}-wrong"))
        world.recordResponse(response.statusCode, response.asString())
    }

    @When("somebody signs in as an account that does not exist")
    fun somebodySignsInAsAnAccountThatDoesNotExist() {
        val response = AcceptanceApi.attemptSignIn(
            TestHelper.RegisteredUser(
                username = "nobody${System.nanoTime()}",
                email = "nobody@systemtest.example.com",
                password = "Passw0rd!nobody",
                discord = "nobody#0000",
                phoneNumber = "+31600000000",
            )
        )
        world.recordResponse(response.statusCode, response.asString())
    }

    @Then("they are signed in")
    fun theyAreSignedIn() {
        assertThat(world.lastStatusCodeOrFail())
            .describedAs("sign-in response, body: ${world.lastResponseBody}")
            .isBetween(200, 204)
    }

    @Then("they are not signed in")
    fun theyAreNotSignedIn() {
        assertThat(world.lastStatusCodeOrFail())
            .describedAs("sign-in must be refused, body: ${world.lastResponseBody}")
            .isGreaterThanOrEqualTo(400)
    }

    @Then("the refusal does not say whether the account exists")
    fun theRefusalDoesNotSayWhetherTheAccountExists() {
        // Whatever it says, it must not name the account or hint that the address
        // is registered: that turns the sign-in form into an account oracle.
        val body = world.lastResponseBody.orEmpty().lowercase()
        assertThat(body).doesNotContain(world.applicant().username.lowercase())
        assertThat(body).doesNotContain("no such user")
        assertThat(body).doesNotContain("unknown user")
    }
}
