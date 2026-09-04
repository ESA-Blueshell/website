package net.blueshell.acceptance.steps

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import net.blueshell.acceptance.AcceptanceApi
import net.blueshell.acceptance.AcceptanceWorld
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat

class SignupSessionSteps(private val world: AcceptanceWorld) {

    @Given("an applicant who is not signed in")
    fun anApplicantWhoIsNotSignedIn() {
        // Nothing to establish: the next step begins the signup.
    }

    @Given("they have begun a membership signup")
    @When("they begin a membership signup")
    fun theyBeginAMembershipSignup() {
        val handle = TestHelper.beginSignup()
        world.rememberApplicant(handle.user)
        world.signupToken = handle.signupToken
    }

    @Given("they have saved their address during signup")
    @When("they save their address during signup")
    fun theySaveTheirAddressDuringSignup() {
        val response = AcceptanceApi.saveSignupAddress(world.signupTokenOrFail())
        world.recordResponse(response.statusCode, response.asString())
    }

    @Given("they have accepted the membership conditions during signup")
    @When("they accept the membership conditions during signup")
    fun theyAcceptTheConditionsDuringSignup() {
        val applicant = world.applicant()
        world.confirmationEmailsBeforeAction = AcceptanceApi.confirmationEmailCount(applicant.email)
        val response = AcceptanceApi.submitSignupApplication(world.signupTokenOrFail())
        world.recordResponse(response.statusCode, response.asString())
    }

    @Given("their signup session has expired")
    fun theirSignupSessionHasExpired() {
        // Age the session out with the database's clock rather than waiting two
        // hours, and rather than writing a JVM timestamp whose meaning depends on
        // how the driver converts zones.
        TestHelper.expireRecoveryToken(world.applicant().username, "SIGNUP_CONTINUATION")
    }

    @When("they use their signup session to {string}")
    fun theyUseTheirSignupSessionTo(attempt: String) {
        val response = AcceptanceApi.attemptWithSignupToken(
            signupToken = world.signupTokenOrFail(),
            attempt = attempt,
            otherUserId = TestHelper.findUser(world.applicant().username)?.id,
        )
        world.recordResponse(response.statusCode, response.asString())
    }

    @Then("their signup session is still usable")
    fun theirSignupSessionIsStillUsable() {
        assertThat(AcceptanceApi.saveSignupAddress(world.signupTokenOrFail()).statusCode)
            .describedAs("the session must survive email confirmation")
            .isEqualTo(204)
    }

    @Then("their signup session is retired")
    fun theirSignupSessionIsRetired() {
        assertThat(AcceptanceApi.saveSignupAddress(world.signupTokenOrFail()).statusCode)
            .describedAs("the session must be spent once the membership started")
            .isGreaterThanOrEqualTo(400)
    }

    @When("they change their first name to {string}")
    fun theyChangeTheirFirstNameTo(firstName: String) {
        val response = AcceptanceApi.updateSignupDetails(
            world.signupTokenOrFail(),
            firstName,
            world.applicant(),
        )
        world.recordResponse(response.statusCode, response.asString())
    }

    @Then("their first name is {string}")
    fun theirFirstNameIs(firstName: String) {
        assertThat(TestHelper.firstNameOf(world.applicant().username))
            .describedAs("the corrected first name reaches the account")
            .isEqualTo(firstName)
    }

    @Then("their address is on file")
    fun theirAddressIsOnFile() {
        assertThat(TestHelper.findAddress(world.applicant().username))
            .describedAs("address for ${world.applicant().username}")
            .isNotNull()
    }
}
