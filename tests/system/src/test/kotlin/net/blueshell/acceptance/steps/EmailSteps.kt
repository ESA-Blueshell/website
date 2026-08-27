package net.blueshell.acceptance.steps

import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import net.blueshell.acceptance.AcceptanceApi
import net.blueshell.acceptance.AcceptanceWorld
import net.blueshell.systemtests.TestHelper
import net.blueshell.systemtests.pollFor
import org.assertj.core.api.Assertions.assertThat

// Delivery is async, so "was sent" polls and "no further mail" compares against a
// baseline — registering always sends one.
class EmailSteps(private val world: AcceptanceWorld) {

    @Then("they are sent a confirmation email")
    fun theyAreSentAConfirmationEmail() {
        TestHelper.assertEmailSent(world.applicant().email, AcceptanceApi.CONFIRMATION_SUBJECT)
    }

    @When("they ask for the confirmation email again")
    fun theyAskForTheConfirmationEmailAgain() {
        val applicant = world.applicant()
        world.confirmationEmailsBeforeAction = AcceptanceApi.confirmationEmailCount(applicant.email)
        val response = AcceptanceApi.resendConfirmation(applicant.username)
        world.recordResponse(response.statusCode, response.asString())
    }

    @Then("another confirmation email is sent to them")
    fun anotherConfirmationEmailIsSentToThem() {
        val applicant = world.applicant()
        val before = world.confirmationEmailsBeforeAction ?: 0
        pollFor("another confirmation email for ${applicant.email} beyond the $before already sent") {
            AcceptanceApi.confirmationEmailCount(applicant.email) > before
        }
    }

    @Then("only the most recent confirmation link works")
    fun onlyTheMostRecentConfirmationLinkWorks() {
        assertThat(TestHelper.outstandingConfirmationLinks(world.applicant().username))
            .describedAs("outstanding confirmation links")
            .isEqualTo(1)
    }

    @Then("no further confirmation email is sent to them")
    fun noFurtherConfirmationEmailIsSentToThem() {
        val before = world.confirmationEmailsBeforeAction
            ?: error("No action recorded a confirmation-email count — this Then needs a When before it.")
        val after = AcceptanceApi.confirmationEmailCount(world.applicant().email)
        assertThat(after)
            .describedAs("confirmation emails to ${world.applicant().email} after the action")
            .isEqualTo(before)
    }
}
