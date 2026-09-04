package net.blueshell.acceptance.steps

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.restassured.http.ContentType
import net.blueshell.systemtests.TestEnvironment
import net.blueshell.acceptance.AcceptanceWorld
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat
import java.time.LocalDate

/**
 * Steps for docs/flows/bulk-contribution-marking. Drives the two bulk endpoints over
 * HTTP and reads the resulting rows straight from the database, so a scenario asserts
 * what was stored rather than what the response claimed. Which reason a refusal names
 * is ContributionBulkControllerIT's to say.
 */
class BulkContributionSteps(private val world: AcceptanceWorld) {

    private var periodId: Long? = null
    private val selection = mutableListOf<Long>()
    private val selectedUsernames = mutableListOf<String>()
    private var deletedUserId: Long? = null
    private var honoraryUserId: Long? = null

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

    @Given("they have marked the selection paid")
    fun theyHaveMarkedPaid() = markSelection("mark-paid", selection)

    @When("they mark the selection paid")
    fun markPaid() = markSelection("mark-paid", selection)

    @When("they mark the selection paid again")
    fun markPaidAgain() = markSelection("mark-paid", selection)

    @When("they mark the selection unpaid")
    fun markUnpaid() = markSelection("mark-unpaid", selection)

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
            .cookie(TestEnvironment.authCookieName, world.authCookiesOrFail().auth)
            .contentType(ContentType.JSON)
            .body("""{"userIds":[$ids],"contributionPeriodId":$periodId}""")
            .`when`()
            .post("/contributions/bulk/$action")
        world.recordResponse(response.statusCode, response.asString())
    }

    private fun paidUserIds(): List<Long> = TestHelper.findContributions(requireNotNull(periodId))
}
