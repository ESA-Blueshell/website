package net.blueshell.acceptance.steps

import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import net.blueshell.acceptance.AcceptanceApi
import net.blueshell.acceptance.AcceptanceWorld
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat

class EmailCorrectionSteps(private val world: AcceptanceWorld) {

    @When("they correct their email address")
    fun theyCorrectTheirEmailAddress() {
        val corrected = "corrected-${world.applicant().username}@systemtest.example.com"
        world.correctedEmail = corrected
        val response = AcceptanceApi.correctSignupEmail(world.signupTokenOrFail(), corrected)
        world.recordResponse(response.statusCode, response.asString())
    }

    @Then("the confirmation email goes to the corrected address")
    fun theConfirmationEmailGoesToTheCorrectedAddress() {
        val corrected = requireNotNull(world.correctedEmail) { "No address was corrected in this scenario." }
        TestHelper.assertEmailSent(corrected, AcceptanceApi.CONFIRMATION_SUBJECT)
    }

    @Then("their address and their acceptance are still on file")
    fun theirAddressAndAcceptanceAreStillOnFile() {
        val username = world.applicant().username
        assertThat(TestHelper.findAddress(username))
            .describedAs("address survives a typo fix")
            .isNotNull()
        val userId = requireNotNull(TestHelper.findUser(username)).id
        assertThat(TestHelper.conditionsAcceptedAt(userId))
            .describedAs("acceptance survives a typo fix")
            .isNotNull()
    }
}
