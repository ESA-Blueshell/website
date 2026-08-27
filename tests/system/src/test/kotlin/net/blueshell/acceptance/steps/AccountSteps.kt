package net.blueshell.acceptance.steps

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import net.blueshell.acceptance.AcceptanceApi
import net.blueshell.acceptance.AcceptanceWorld
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat

class AccountSteps(private val world: AcceptanceWorld) {

    @Given("an applicant who has registered an account")
    fun anApplicantWhoHasRegisteredAnAccount() {
        world.rememberApplicant(TestHelper.register())
    }

    @Given("an applicant with an account they can sign in to")
    fun anApplicantWithAnAccountTheyCanSignInTo() {
        world.rememberApplicant(TestHelper.registerAndActivate())
    }

    @Given("they have confirmed their email address")
    @When("they confirm their email address")
    fun theyConfirmTheirEmailAddress() {
        val applicant = world.applicant()
        world.confirmationEmailsBeforeAction = AcceptanceApi.confirmationEmailCount(applicant.email)
        val token = TestHelper.mintRecoveryToken(applicant.username, "USER_ACTIVATION")
        val response = AcceptanceApi.confirmEmailAddress(token)
        world.recordResponse(response.statusCode, response.asString())
    }

    @Then("their account is usable")
    fun theirAccountIsUsable() {
        val applicant = world.applicant()
        assertThat(TestHelper.findUser(applicant.username)?.enabled)
            .describedAs("account enabled flag for ${applicant.username}")
            .isTrue()
        assertThat(AcceptanceApi.attemptSignIn(applicant).statusCode)
            .describedAs("sign-in for ${applicant.username}")
            .isBetween(200, 204)
    }

    @Then("their account is not yet usable")
    fun theirAccountIsNotYetUsable() {
        val applicant = world.applicant()
        assertThat(TestHelper.findUser(applicant.username)?.enabled)
            .describedAs("account enabled flag for ${applicant.username}")
            .isFalse()
        assertThat(AcceptanceApi.attemptSignIn(applicant).statusCode)
            .describedAs("sign-in for ${applicant.username} must be rejected")
            .isGreaterThanOrEqualTo(400)
    }
}
