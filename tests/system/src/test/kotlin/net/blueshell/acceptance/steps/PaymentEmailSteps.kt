package net.blueshell.acceptance.steps

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.restassured.http.ContentType
import io.restassured.path.json.JsonPath
import net.blueshell.acceptance.AcceptanceWorld
import net.blueshell.systemtests.TestEnvironment
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import java.time.LocalDate

/**
 * Steps for docs/flows/payment-emails. Drives the send over HTTP and reads the resulting
 * rows straight from the database, so a scenario asserts what was stored rather than what
 * the response claimed.
 */
class PaymentEmailSteps(private val world: AcceptanceWorld) {

    private companion object {
        const val REMINDERS = "contribution_reminders"
        const val NOTIFICATIONS = "incasso_notifications"
    }

    private var periodId: Long? = null
    private val selection = mutableListOf<Long>()
    private var transferUserId: Long? = null
    private var directDebitUserId: Long? = null
    private var honoraryUserId: Long? = null
    private var paidUserId: Long? = null

    @Given("a contribution period they can send payment emails for")
    fun aContributionPeriod() {
        periodId = TestHelper.createContributionPeriod(
            startDate = LocalDate.now().minusMonths(6),
            endDate = LocalDate.now().plusMonths(6),
            fullYearFee = 40.0,
            halfYearFee = 20.0,
            alumniFee = 10.0,
        )
    }

    @Given("a member who pays by transfer")
    fun aTransferMember() {
        transferUserId = addMember(incasso = false)
    }

    @Given("a member who pays by direct debit")
    fun aDirectDebitMember() {
        directDebitUserId = addMember(incasso = true)
    }

    @Given("a member who has already paid for the period")
    fun aPaidMember() {
        paidUserId = addMember(incasso = false, paid = true)
    }

    @Given("an honorary member in the selection")
    fun anHonoraryMember() {
        honoraryUserId = addMember(incasso = false, memberType = "HONORARY")
    }

    // ── Sending ──────────────────────────────────────────────────────────────

    @When("they send the payment emails")
    fun send() = post()

    @When("they send the payment emails to nobody")
    fun sendToNobody() = post(userIds = emptyList())

    @When("they send the payment emails without a payment due date")
    fun sendWithoutDueDate() = post(paymentDueDate = null)

    @When("they send the payment emails without a debit date")
    fun sendWithoutDebitDate() = post(debitDate = null)

    @When("they move that member onto the contribution reminder and send")
    fun sendWithDirectDebitMemberSwitched() =
        post(kindOverrides = mapOf(requireNotNull(directDebitUserId) to "REMINDER"))

    @When("they move the honorary member onto the contribution reminder and send")
    fun sendWithHonoraryMemberSwitched() =
        post(kindOverrides = mapOf(requireNotNull(honoraryUserId) to "REMINDER"))

    @When("they forcibly include that member and send")
    fun sendForciblyIncluding() =
        post(forciblyIncluded = listOfNotNull(paidUserId, honoraryUserId))

    @When("they send the payment emails charging that member the alumni fee")
    fun sendChargingAlumniFee() =
        post(feeTypeOverrides = mapOf(requireNotNull(transferUserId) to "ALUMNI_FEE"))

    @When("they send the payment emails charging the honorary member the alumni fee")
    fun sendChargingHonoraryAlumniFee() =
        post(feeTypeOverrides = mapOf(requireNotNull(honoraryUserId) to "ALUMNI_FEE"))

    // ── Assertions ───────────────────────────────────────────────────────────

    @Then("the member who pays by transfer is sent a contribution reminder")
    fun transferMemberReminded() {
        assertThat(recipients(REMINDERS)).contains(requireNotNull(transferUserId))
    }

    @Then("the member who pays by direct debit is sent an incasso notification")
    fun directDebitMemberNotified() {
        assertThat(recipients(NOTIFICATIONS)).contains(requireNotNull(directDebitUserId))
    }

    @Then("the member who pays by direct debit is sent a contribution reminder")
    fun directDebitMemberReminded() {
        assertThat(recipients(REMINDERS)).contains(requireNotNull(directDebitUserId))
    }

    @Then("no incasso notification is recorded")
    fun noNotifications() {
        assertThat(recipients(NOTIFICATIONS)).isEmpty()
    }

    @Then("the honorary member is sent nothing")
    fun honoraryMemberSentNothing() {
        val id = requireNotNull(honoraryUserId)
        assertThat(recipients(REMINDERS)).doesNotContain(id)
        assertThat(recipients(NOTIFICATIONS)).doesNotContain(id)
    }

    @Then("nothing is recorded for the period")
    fun nothingRecorded() {
        assertThat(recipients(REMINDERS)).isEmpty()
        assertThat(recipients(NOTIFICATIONS)).isEmpty()
    }

    @Then("that member has {int} contribution reminders recorded")
    fun remindersRecordedForMember(expected: Int) {
        val id = requireNotNull(transferUserId)
        assertThat(recipients(REMINDERS).count { it == id }).isEqualTo(expected)
    }

    @Then("the recorded contribution reminder states the alumni fee")
    fun reminderStatesAlumniFee() {
        val written = rows(REMINDERS).single { it.userId == transferUserId }
        assertThat(written.feeType).isEqualTo("ALUMNI_FEE")
        assertThat(written.amount).isEqualTo(10.0)
    }

    @Then("{int} contribution reminder and {int} incasso notification are reported")
    fun countsReportedSingular(reminders: Int, notifications: Int) =
        assertCounts(reminders, notifications)

    @Then("{int} contribution reminder and {int} incasso notifications are reported")
    fun countsReported(reminders: Int, notifications: Int) = assertCounts(reminders, notifications)

    @Then("{int} member is reported as not written to")
    fun notWrittenTo(expected: Int) {
        assertThat(body().getInt("notWrittenTo")).isEqualTo(expected)
    }

    // ── Fixture ──────────────────────────────────────────────────────────────

    private fun assertCounts(reminders: Int, notifications: Int) {
        assertThat(body().getInt("remindersSent")).isEqualTo(reminders)
        assertThat(body().getInt("incassoNotificationsSent")).isEqualTo(notifications)
    }

    private fun addMember(
        incasso: Boolean,
        memberType: String = "REGULAR",
        paid: Boolean = false,
    ): Long {
        val user = TestHelper.registerAndActivate()
        world.createdUsernames += user.username
        TestHelper.attachMembership(user.username, memberType = memberType, incasso = incasso)
        if (paid) TestHelper.createContribution(requireNotNull(periodId), user.username)
        val id = requireNotNull(TestHelper.findUser(user.username)).id
        selection += id
        return id
    }

    private fun post(
        userIds: List<Long> = selection,
        forciblyIncluded: List<Long> = emptyList(),
        kindOverrides: Map<Long, String> = emptyMap(),
        feeTypeOverrides: Map<Long, String> = emptyMap(),
        paymentDueDate: LocalDate? = LocalDate.now().plusMonths(1),
        debitDate: LocalDate? = LocalDate.now().plusMonths(1).plusDays(14),
    ) {
        val body = buildString {
            append("""{"contributionPeriodId":$periodId""")
            append(""","userIds":[${userIds.joinToString(",")}]""")
            append(""","forciblyIncludedUserIds":[${forciblyIncluded.joinToString(",")}]""")
            append(""","kindOverrides":${asJsonObject(kindOverrides)}""")
            append(""","feeTypeOverrides":${asJsonObject(feeTypeOverrides)}""")
            append(""","paymentDueDate":${paymentDueDate.asJson()}""")
            append(""","debitDate":${debitDate.asJson()}}""")
        }
        val response = TestHelper.givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .cookie(TestEnvironment.authCookieName, world.authCookiesOrFail().auth)
            .contentType(ContentType.JSON)
            .body(body)
            .`when`()
            .post("/contributions/bulk/email/send")
        world.recordResponse(response.statusCode, response.asString())
    }

    private fun asJsonObject(values: Map<Long, String>): String =
        values.entries.joinToString(",", "{", "}") { """"${it.key}":"${it.value}"""" }

    private fun LocalDate?.asJson(): String = if (this == null) "null" else "\"$this\""

    private fun rows(table: String) = TestHelper.findPaymentEmails(table, requireNotNull(periodId))

    private fun recipients(table: String): List<Long> = rows(table).map { it.userId }

    private fun body(): JsonPath = JsonPath.from(requireNotNull(world.lastResponseBody))
}
