package net.blueshell.acceptance.steps

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import net.blueshell.acceptance.AcceptanceWorld
import net.blueshell.acceptance.Inbox
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

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

    /**
     * Two weeks as the api counted them.
     *
     * Both sides are DATEs the api wrote, so this suite contributes no clock and needs no zone. Reading
     * `LocalDate.now()` here instead would need the literal and the `TZ` in docker-compose.ci.yml to agree,
     * with nothing making them move together. The span is the guarantee itself, not a coincidence of two
     * clock reads: `JoiningContributionAskService` counts the window from the membership start date, so
     * fourteen days between those two stored dates is what the association promises.
     */
    @Then("they are given two weeks to pay")
    fun theyAreGivenTwoWeeks() {
        val body = awaitWelcomeEmail().htmlContent
        val due = requireNotNull(askOnRecord().paymentDueDate) { "the ask recorded no due date" }
        val start = requireNotNull(TestHelper.activeMembershipStartDate(world.applicant().username)) {
            "the new member has no active membership to count from"
        }

        assertThat(ChronoUnit.DAYS.between(start, due))
            .describedAs("days from the membership starting to the payment falling due")
            .isEqualTo(14)
        assertThat(body)
            .describedAs("the email shows the due date the api recorded")
            .contains(renderedDate(due))
    }

    @Then("the asking is on record")
    fun theAskingIsOnRecord() {
        awaitWelcomeEmail()
        val ask = askOnRecord()

        assertThat(ask.feeType).isEqualTo("FULL_YEAR_FEE")
        assertThat(ask.amount).isEqualTo(FULL_YEAR_FEE)
    }

    /** The one ask this applicant has for the period under test. */
    private fun askOnRecord(): TestHelper.PaymentEmailRow {
        val asks = TestHelper.findPaymentEmails(REMINDERS, requireNotNull(periodId))
            .filter { it.userId == world.applicantId() }

        assertThat(asks).hasSize(1)
        return asks.single()
    }

    @Then("they are not asked to pay anything")
    fun theyAreNotAsked() {
        // Waited out rather than asked once: the ask is queued, so a single immediate query
        // would run before a wrongly-queued email could have been delivered and would agree
        // with the bug it exists to catch.
        Inbox.awaitNothing(world.applicant().email, WELCOME_SUBJECT)
    }

    /**
     * Waits for the welcome email. The send is queued after the membership commits, so it
     * arrives a moment after the step that made somebody a member returned.
     */
    private fun awaitWelcomeEmail(): TestHelper.SentEmail =
        Inbox.await(world.applicant().email, WELCOME_SUBJECT, world.lastStatusCode, world.lastResponseBody)

    /**
     * The due date as the member reads it, e.g. "17 September 2026".
     *
     * A second implementation of `DATE_FORMATTER` in `ContributionReminderEmailBuilder.kt`,
     * not a shared one: this suite drives the api over http and deliberately carries no
     * dependency on it (see `tests/system/build.gradle.kts`). Changing the format there means
     * changing it here.
     */
    private fun renderedDate(date: LocalDate): String =
        "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${date.year}"
}
