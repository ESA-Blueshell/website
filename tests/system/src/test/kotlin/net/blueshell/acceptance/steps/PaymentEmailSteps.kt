package net.blueshell.acceptance.steps

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.restassured.http.ContentType
import net.blueshell.acceptance.AcceptanceWorld
import net.blueshell.systemtests.TestEnvironment
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import java.time.LocalDate

/**
 * Steps for docs/flows/payment-emails.
 *
 * These assert what the association guarantees: what arrived in the member's inbox, what it
 * said, and what the record shows afterwards. Which status code the send answered, and which
 * field a refusal names, are `BulkContributionEmailControllerIT`'s to assert — asserting them
 * here would only repeat them through a slower driver.
 */
class PaymentEmailSteps(private val world: AcceptanceWorld) {

    private companion object {
        const val REMINDERS = "contribution_reminders"

        /**
         * How the outbox tells the two emails apart. Matched as a fragment: both subjects end
         * in the academic year, which depends on the period the scenario made.
         */
        const val REMINDER_SUBJECT = "Please pay your Blueshell contribution"
        const val NOTIFICATION_SUBJECT = "will be collected automatically"

        const val DELIVERY_TIMEOUT_MS = 15_000L

        const val FULL_YEAR_FEE = 40.0
        const val ALUMNI_FEE = 10.0
    }

    private var periodId: Long? = null
    private val selection = mutableListOf<Long>()
    private var transferMember: TestHelper.RegisteredUser? = null
    private var directDebitMember: TestHelper.RegisteredUser? = null
    private var honoraryMember: TestHelper.RegisteredUser? = null
    private var lastEmail: TestHelper.SentEmail? = null

    /** The member the scenario is about, when it has only one. */
    private fun subject(): TestHelper.RegisteredUser =
        transferMember ?: directDebitMember
            ?: error("This scenario has no member — start it with a Given that adds one.")

    @Given("a contribution period they can send payment emails for")
    fun aContributionPeriod() {
        periodId = TestHelper.createContributionPeriod(
            startDate = LocalDate.now().minusMonths(6),
            endDate = LocalDate.now().plusMonths(6),
            fullYearFee = FULL_YEAR_FEE,
            halfYearFee = 20.0,
            alumniFee = ALUMNI_FEE,
        )
    }

    @Given("a member who pays by transfer")
    fun aTransferMember() {
        transferMember = addMember(incasso = false)
    }

    @Given("a member who pays by direct debit")
    fun aDirectDebitMember() {
        directDebitMember = addMember(incasso = true)
    }

    @Given("an honorary member among the selected")
    fun anHonoraryMember() {
        honoraryMember = addMember(incasso = false, memberType = "HONORARY")
    }

    // ── Sending ──────────────────────────────────────────────────────────────

    @When("they send the payment emails")
    fun send() = post()

    @When("they move that member onto the contribution reminder and send")
    fun sendSwitched() = post(kindOverrides = mapOf(idOf(subject()) to "REMINDER"))

    @When("they send the payment emails charging that member the alumni fee")
    fun sendChargingAlumniFee() = post(feeTypeOverrides = mapOf(idOf(subject()) to "ALUMNI_FEE"))

    // ── What the member received ─────────────────────────────────────────────

    @Then("that member receives a contribution reminder")
    fun receivesReminder() {
        lastEmail = awaitEmail(subject(), REMINDER_SUBJECT)
    }

    @Then("that member receives an incasso notification")
    fun receivesNotification() {
        lastEmail = awaitEmail(subject(), NOTIFICATION_SUBJECT)
    }

    @Then("each member receives the email their payment method calls for")
    fun eachReceivesTheirOwn() {
        awaitEmail(requireNotNull(transferMember), REMINDER_SUBJECT)
        awaitEmail(requireNotNull(directDebitMember), NOTIFICATION_SUBJECT)
    }

    @Then("it states the {word} fee and what it comes to")
    fun itStatesTheFee(fee: String) {
        val (reason, amount) = when (fee) {
            "full-year" -> "the full-year fee" to FULL_YEAR_FEE
            "alumni" -> "as you are an alumni member" to ALUMNI_FEE
            else -> error("Unknown fee: $fee")
        }
        // The amount without its symbol: the body is rendered HTML, where a € may arrive as
        // an entity, and the digits are the part the assertion is about. Dutch notation,
        // built rather than formatted, so a JVM default locale cannot change it.
        val money = "${amount.toInt()},%02d".format((amount * 100).toInt() % 100)
        assertThat(body()).contains(money).contains(reason)
    }

    @Then("it says where to transfer the money")
    fun itSaysWhereToPay() {
        assertThat(body()).contains("Bank transfer")
    }

    @Then("it asks them to transfer nothing")
    fun itAsksForNoTransfer() {
        assertThat(body())
            .contains("do not need to transfer anything")
            .doesNotContain("Bank transfer")
    }

    @Then("they are not told that anything will be taken from their account")
    fun notToldAboutADebit() {
        assertThat(body()).doesNotContain("collected from your bank account")
    }

    @Then("the honorary member receives no payment email")
    fun honoraryReceivesNothing() {
        // Waiting for the other member's email first, so an absent one here is the answer
        // rather than a race. Their inbox is not empty — creating a member sends them an
        // activation email — so this asks only about payment emails.
        awaitEmail(requireNotNull(transferMember), REMINDER_SUBJECT)
        val theirs = TestHelper.findEmails(recipient = requireNotNull(honoraryMember).email)
        assertThat(theirs.map { it.subject }.filter(::isPaymentEmail)).isEmpty()
    }

    @Then("that member has been asked twice for this period")
    fun askedTwice() {
        val id = idOf(subject())
        val asks = TestHelper.findPaymentEmails(REMINDERS, requireNotNull(periodId))
            .count { it.userId == id }
        assertThat(asks).isEqualTo(2)
    }

    // ── Fixture ──────────────────────────────────────────────────────────────

    /**
     * Waits for one of this member's emails to carry [subjectFragment].
     *
     * Filtered by recipient rather than by subject: the outbox matches a subject exactly, and
     * both of these end in the academic year the scenario's period works out to.
     *
     * A send that was refused shows up here as nothing arriving, so the failure quotes what
     * the send answered — otherwise every cause reads as "no email came".
     */
    private fun awaitEmail(
        member: TestHelper.RegisteredUser,
        subjectFragment: String,
    ): TestHelper.SentEmail {
        val deadline = System.currentTimeMillis() + DELIVERY_TIMEOUT_MS
        var seen: List<TestHelper.SentEmail> = emptyList()
        while (System.currentTimeMillis() < deadline) {
            seen = TestHelper.findEmails(recipient = member.email)
            seen.firstOrNull { it.subject.contains(subjectFragment) }?.let { return it }
            Thread.sleep(250)
        }
        throw AssertionError(
            "No email to ${member.email} with a subject containing \"$subjectFragment\" " +
                "within ${DELIVERY_TIMEOUT_MS}ms. " +
                "The send answered ${world.lastStatusCode}: ${world.lastResponseBody}. " +
                "That inbox holds: ${seen.map { it.subject }}",
        )
    }

    private fun isPaymentEmail(subject: String): Boolean =
        subject.contains(REMINDER_SUBJECT) || subject.contains(NOTIFICATION_SUBJECT)

    private fun body(): String =
        requireNotNull(lastEmail) { "No email has been read yet in this scenario." }.htmlContent

    private fun idOf(user: TestHelper.RegisteredUser): Long =
        requireNotNull(TestHelper.findUser(user.username)).id

    private fun addMember(
        incasso: Boolean,
        memberType: String = "REGULAR",
    ): TestHelper.RegisteredUser {
        val user = TestHelper.registerAndActivate()
        world.createdUsernames += user.username
        TestHelper.attachMembership(user.username, memberType = memberType, incasso = incasso)
        selection += idOf(user)
        return user
    }

    private fun post(
        kindOverrides: Map<Long, String> = emptyMap(),
        feeTypeOverrides: Map<Long, String> = emptyMap(),
    ) {
        val body = buildString {
            append("""{"contributionPeriodId":$periodId""")
            append(""","userIds":[${selection.joinToString(",")}]""")
            append(""","kindOverrides":${asJsonObject(kindOverrides)}""")
            append(""","feeTypeOverrides":${asJsonObject(feeTypeOverrides)}""")
            append(""","paymentDueDate":"${LocalDate.now().plusMonths(1)}"""")
            append(""","debitDate":"${LocalDate.now().plusMonths(1).plusDays(14)}"}""")
        }
        val response = TestHelper.givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .cookie(TestEnvironment.authCookieName, world.authCookiesOrFail().auth)
            .contentType(ContentType.JSON)
            .body(body)
            .`when`()
            .post("/contributions/bulk/email/send")
        // Recorded, not asserted: a scenario here is about what the member received, and a
        // send that answers 200 while delivering nothing is the failure worth seeing.
        world.recordResponse(response.statusCode, response.asString())
    }

    private fun asJsonObject(values: Map<Long, String>): String =
        values.entries.joinToString(",", "{", "}") { """"${it.key}":"${it.value}"""" }
}
