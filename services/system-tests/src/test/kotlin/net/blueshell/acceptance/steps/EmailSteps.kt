package net.blueshell.acceptance.steps

import io.cucumber.java.en.Then
import net.blueshell.acceptance.AcceptanceApi
import net.blueshell.acceptance.AcceptanceWorld
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat

// Delivery is async, so "was sent" polls and "no further mail" compares against a
// baseline — registering always sends one.
class EmailSteps(private val world: AcceptanceWorld) {

    @Then("they are sent a confirmation email")
    fun theyAreSentAConfirmationEmail() {
        TestHelper.assertEmailSent(world.applicant().email, AcceptanceApi.CONFIRMATION_SUBJECT)
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
