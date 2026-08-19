package net.blueshell.acceptance.steps

import io.cucumber.java.PendingException
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import net.blueshell.acceptance.AcceptanceWorld

// Bound but unimplemented, so -PcucumberTags="@pending" prints an outstanding list
// instead of undefined-step snippets. Empty and deleted when the feature is done.
class PendingSignupSteps(@Suppress("unused") private val world: AcceptanceWorld) {

    private fun notBuiltYet(what: String): Nothing =
        throw PendingException(
            "$what is specified by ADR-024 / ADR-025 but not implemented yet. " +
                "See docs/flows/membership-signup/README.md.",
        )

    @Given("an applicant who is not signed in")
    fun anApplicantWhoIsNotSignedIn(): Unit = notBuiltYet("POST /signup")

    @Given("they have begun a membership signup")
    @When("they begin a membership signup")
    fun theyBeginAMembershipSignup(): Unit = notBuiltYet("POST /signup")

    @Given("they have saved their address during signup")
    @When("they save their address during signup")
    fun theySaveTheirAddressDuringSignup(): Unit = notBuiltYet("POST /signup/address")

    @Given("they have accepted the membership conditions during signup")
    @When("they accept the membership conditions during signup")
    fun theyAcceptTheConditionsDuringSignup(): Unit = notBuiltYet("POST /signup/apply")

    @When("they correct their email address")
    fun theyCorrectTheirEmailAddress(): Unit = notBuiltYet("PATCH /signup/email")

    @Given("their signup session has expired")
    fun theirSignupSessionHasExpired(): Unit = notBuiltYet("signup-session expiry")

    @When("they use their signup session to {string}")
    fun theyUseTheirSignupSessionTo(@Suppress("unused") attempt: String): Unit =
        notBuiltYet("signup-session scope enforcement")

    @Then("their signup session is still usable")
    fun theirSignupSessionIsStillUsable(): Unit = notBuiltYet("signup-session lifecycle")

    @Then("their signup session is retired")
    fun theirSignupSessionIsRetired(): Unit = notBuiltYet("signup-session retirement")

    @Then("the confirmation email goes to the corrected address")
    fun theConfirmationEmailGoesToTheCorrectedAddress(): Unit = notBuiltYet("PATCH /signup/email")

    @Then("their address and their acceptance are still on file")
    fun theirAddressAndAcceptanceAreStillOnFile(): Unit = notBuiltYet("conditionsAcceptedAt")

    @Then("the request is refused")
    fun theRequestIsRefused(): Unit = notBuiltYet("signup-session scope enforcement")
}
