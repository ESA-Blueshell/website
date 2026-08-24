package net.blueshell.acceptance.steps

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.restassured.http.ContentType
import io.restassured.path.json.JsonPath
import net.blueshell.systemtests.TestEnvironment
import net.blueshell.acceptance.AcceptanceWorld
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import java.time.LocalDate

/**
 * Steps for docs/flows/bulk-contribution-marking. Drives the two bulk endpoints over
 * HTTP and reads the resulting rows straight from the database, so a scenario asserts
 * what was stored rather than what the response claimed.
 */
class BulkContributionSteps(private val world: AcceptanceWorld) {

    private var cookies: TestHelper.LoginCookies? = null
    private var periodId: Long? = null
    private val selection = mutableListOf<Long>()
    private val selectedUsernames = mutableListOf<String>()
    private var deletedUserId: Long? = null
    private var honoraryUserId: Long? = null

    @Given("a board member signed in to the user manager")
    fun aBoardMemberSignedIn() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        world.createdUsernames += board.username
        cookies = TestHelper.login(board)
    }

    @Given("a {string} signed in")
    fun aRoleSignedIn(role: String) {
        val user = TestHelper.registerActivateAndPromote(role)
        world.createdUsernames += user.username
        cookies = TestHelper.login(user)
    }

    @Given("a contribution period they can record against")
    fun aContributionPeriod() {
        periodId = TestHelper.createContributionPeriod(
            startDate = LocalDate.now().minusMonths(6),
            endDate = LocalDate.now().plusMonths(6),
            fullYearFee = 40.0,
            halfYearFee = 20.0,
            alumniFee = 10.0,
        )
    }

    @Given("a member with no contribution for the period")
    fun aMemberWithNoContribution() = addMembers(1, paid = false)

    @Given("two members with no contribution for the period")
    fun twoMembersWithNoContribution() = addMembers(2, paid = false)

    @Given("a member with a contribution for the period")
    fun aMemberWithAContribution() = addMembers(1, paid = true)

    @Given("two members with a contribution for the period")
    fun twoMembersWithAContribution() = addMembers(2, paid = true)

    @Given("an honorary member in the selection")
    fun anHonoraryMember() {
        val user = TestHelper.registerAndActivate()
        world.createdUsernames += user.username
        TestHelper.attachMembership(user.username, memberType = "HONORARY")
        val id = requireNotNull(TestHelper.findUser(user.username)).id
        honoraryUserId = id
        selection += id
        selectedUsernames += user.username
    }

    @Given("a user in the selection who has since been deleted")
    fun aDeletedUser() {
        val user = TestHelper.registerAndActivate()
        TestHelper.attachMembership(user.username)
        val id = requireNotNull(TestHelper.findUser(user.username)).id
        TestHelper.eraseUser(user.username)
        deletedUserId = id
        selection += id
    }

    @Given("an id in the selection that was never a user")
    fun anIdThatWasNeverAUser() {
        selection += 9_999_999L
    }

    @Given("the contribution period has since been deleted")
    fun thePeriodWasDeleted() {
        TestHelper.deleteContributionPeriod(requireNotNull(periodId))
    }

    @Given("they have marked the selection paid")
    fun theyHaveMarkedPaid() = markSelection("mark-paid", selection)

    @When("they mark the selection paid")
    fun markPaid() = markSelection("mark-paid", selection)

    @When("they mark the selection paid again")
    fun markPaidAgain() = markSelection("mark-paid", selection)

    @When("they mark the selection unpaid")
    fun markUnpaid() = markSelection("mark-unpaid", selection)

    @When("they mark an empty selection paid")
    fun markEmptySelection() = markSelection("mark-paid", emptyList())

    @Then("the request succeeds")
    fun requestSucceeds() {
        assertThat(world.lastStatusCodeOrFail()).isEqualTo(200)
    }

    @Then("the request is refused as a conflict")
    fun requestRefusedAsConflict() {
        assertThat(world.lastStatusCodeOrFail()).isEqualTo(409)
    }

    @Then("the request is refused as invalid")
    fun requestRefusedAsInvalid() {
        assertThat(world.lastStatusCodeOrFail()).isEqualTo(400)
    }

    @Then("the request is forbidden")
    fun requestForbidden() {
        assertThat(world.lastStatusCodeOrFail()).isEqualTo(403)
    }

    @Then("the refusal reports {string} against {string}")
    fun refusalReports(code: String, field: String) {
        val errors = errors()
        assertThat(errors.map { it["code"] }).contains(code)
        assertThat(errors.single { it["code"] == code }["field"]).isEqualTo(field)
    }

    @Then("the refusal reports both {string} and {string}")
    fun refusalReportsBoth(first: String, second: String) {
        assertThat(errors().map { it["code"] }).contains(first, second)
    }

    @Then("the refusal names the deleted user")
    fun refusalNamesDeletedUser() {
        assertThat(namedIds()).contains(requireNotNull(deletedUserId))
    }

    @Then("the refusal names the honorary member")
    fun refusalNamesHonoraryMember() {
        assertThat(namedIds()).contains(requireNotNull(honoraryUserId))
    }

    @Then("the refusal names the id that was never a user")
    fun refusalNamesUnknownId() {
        assertThat(namedIds()).contains(9_999_999L)
    }

    @Then("both members have a contribution for the period")
    fun bothMembersPaid() {
        assertThat(paidUserIds()).containsAll(selection)
    }

    @Then("neither member has a contribution for the period")
    fun neitherMemberPaid() {
        assertThat(paidUserIds()).doesNotContainAnyElementsOf(selection)
    }

    @Then("the remaining member has no contribution for the period")
    fun remainingMemberNotPaid() {
        val survivors = selection.filter { it != deletedUserId && it != honoraryUserId }
        assertThat(paidUserIds()).doesNotContainAnyElementsOf(survivors)
    }

    @Then("the member has exactly one contribution for the period")
    fun exactlyOneContribution() {
        val userId = selection.single { it != deletedUserId && it != honoraryUserId }
        assertThat(paidUserIds().count { it == userId }).isEqualTo(1)
    }

    @Then("{int} rows are reported as applied")
    fun rowsApplied(expected: Int) {
        assertThat(body().getInt("applied")).isEqualTo(expected)
    }

    @Then("{int} row is reported as unchanged")
    fun rowUnchanged(expected: Int) {
        assertThat(body().getInt("skipped")).isEqualTo(expected)
    }

    private fun addMembers(count: Int, paid: Boolean) {
        repeat(count) {
            val user = TestHelper.registerAndActivate()
            world.createdUsernames += user.username
            TestHelper.attachMembership(user.username)
            if (paid) TestHelper.createContribution(requireNotNull(periodId), user.username)
            selection += requireNotNull(TestHelper.findUser(user.username)).id
            selectedUsernames += user.username
        }
    }

    private fun markSelection(action: String, userIds: List<Long>) {
        val ids = userIds.joinToString(",")
        val response = TestHelper.givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .cookie(TestEnvironment.authCookieName, requireNotNull(cookies).auth)
            .contentType(ContentType.JSON)
            .body("""{"userIds":[$ids],"contributionPeriodId":$periodId}""")
            .`when`()
            .post("/contributions/bulk/$action")
        world.recordResponse(response.statusCode, response.asString())
    }

    private fun body(): JsonPath = JsonPath.from(requireNotNull(world.lastResponseBody))

    private fun errors(): List<Map<String, Any?>> =
        body().getList<Map<String, Any?>>("errors")

    /** Every id the refusal named, across all reasons; which code carries one is asserted separately. */
    private fun namedIds(): List<Long> =
        errors().flatMap { (it["values"] as? List<*>).orEmpty() }.mapNotNull { (it as? Number)?.toLong() }

    private fun paidUserIds(): List<Long> = TestHelper.findContributions(requireNotNull(periodId))
}
