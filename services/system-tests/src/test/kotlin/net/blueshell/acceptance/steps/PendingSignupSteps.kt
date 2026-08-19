package net.blueshell.acceptance.steps

import io.cucumber.java.PendingException
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import net.blueshell.acceptance.AcceptanceWorld

// Bound but unimplemented, so -PcucumberTags="@pending" prints an outstanding list
// instead of undefined-step snippets. Empty and deleted when the feature is done.
class PendingSignupSteps(@Suppress("unused") private val world: AcceptanceWorld) {

    private fun notBuiltYet(what: String): Nothing =
        throw PendingException("$what is not implemented yet. See ADR-024.")

    @When("they correct their email address")
    fun theyCorrectTheirEmailAddress(): Nothing = notBuiltYet("PATCH /signup/email")

    @Then("the confirmation email goes to the corrected address")
    fun theConfirmationEmailGoesToTheCorrectedAddress(): Nothing = notBuiltYet("PATCH /signup/email")

    @Then("their address and their acceptance are still on file")
    fun theirAddressAndAcceptanceAreStillOnFile(): Nothing = notBuiltYet("PATCH /signup/email")
}
