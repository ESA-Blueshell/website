package net.blueshell.acceptance.steps

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import net.blueshell.acceptance.AcceptanceWorld
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import java.time.LocalDate

/**
 * Steps for the ask a new member gets on joining — see docs/flows/membership-signup.
 *
 * These assert what the association guarantees: that somebody who joins is told what they
 * owe and how to pay it, and that the asking is on record. Which job queued it and which
 * table it landed in are the unit and integration suites' business.
 */
class JoiningContributionSteps(private val world: AcceptanceWorld) {

    private companion object {
        const val REMINDERS = "contribution_reminders"
        const val WELCOME_SUBJECT = "Welcome to Blueshell Esports"
        const val DELIVERY_TIMEOUT_MS = 15_000L

        const val FULL_YEAR_FEE = 20.0
    }

    private var periodId: Long? = null

    @Given("a contribution period covering today")
    fun aContributionPeriod() {
        periodId = TestHelper.createContributionPeriod(
            startDate = LocalDate.now().minusMonths(1),
            endDate = LocalDate.now().plusMonths(11),
            fullYearFee = FULL_YEAR_FEE,
            halfYearFee = 12.50,
            alumniFee = 10.0,
            // After today, so a membership starting now is a full year rather than half of one.
            halfYearCutoffDate = LocalDate.now().plusMonths(5),
        )
    }

    @Then("they are told what they owe and how to pay it")
    fun theyAreToldHowToPay() {
        val body = awaitWelcomeEmail().htmlContent

        assertThat(body)
            // The amount without its symbol: the body is rendered HTML, where a € may arrive
            // as an entity, and the digits are the part the assertion is about.
            .contains("20,00")
            .contains("the full-year fee")
            .contains("Bank transfer")
            .contains("postbus 49")
            .contains("Direct debit")
    }

    @Then("they are given two weeks to pay")
    fun theyAreGivenTwoWeeks() {
        val due = LocalDate.now().plusWeeks(2)
        // Rendered the way the builder formats a date, e.g. "17 September 2026".
        val rendered = "${due.dayOfMonth} ${
            due.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
        } ${due.year}"

        assertThat(awaitWelcomeEmail().htmlContent).contains(rendered)
    }

    @Then("the asking is on record")
    fun theAskingIsOnRecord() {
        awaitWelcomeEmail()
        val asks = TestHelper.findPaymentEmails(REMINDERS, requireNotNull(periodId))
            .filter { it.userId == world.applicantId() }

        assertThat(asks).hasSize(1)
        assertThat(asks.single().feeType).isEqualTo("FULL_YEAR_FEE")
        assertThat(asks.single().amount).isEqualTo(FULL_YEAR_FEE)
    }

    @Then("they are not asked to pay anything")
    fun theyAreNotAsked() {
        val theirs = TestHelper.findEmails(recipient = world.applicant().email)
        assertThat(theirs.map { it.subject }.filter { it.contains(WELCOME_SUBJECT) }).isEmpty()
    }

    /**
     * Waits for the welcome email. The send is queued after the membership commits, so it
     * arrives a moment after the step that made somebody a member returned.
     */
    private fun awaitWelcomeEmail(): TestHelper.SentEmail {
        val deadline = System.currentTimeMillis() + DELIVERY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            TestHelper.findEmails(recipient = world.applicant().email)
                .firstOrNull { it.subject.contains(WELCOME_SUBJECT) }
                ?.let { return it }
            Thread.sleep(250)
        }
        error("No \"$WELCOME_SUBJECT\" email reached ${world.applicant().email} within ${DELIVERY_TIMEOUT_MS}ms")
    }
}
