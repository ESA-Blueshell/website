package net.blueshell.api.system.frontend.events

import net.blueshell.api.system.frontend.helper.AuthHelper
import net.blueshell.api.system.frontend.helper.SelectHelper
import net.blueshell.api.system.frontend.helper.TestIdLocatorHelper
import net.blueshell.systemtests.PlaywrightTestBase
import net.blueshell.systemtests.TestHelper
import net.blueshell.systemtests.awaitResponseFrom
import net.blueshell.systemtests.pollForValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * The reveal, the one step of an annual vote that cannot be undone, run for real: the job the
 * scheduler queues counts a vote whose reveal date has passed, stores its top, deletes every
 * ballot, and the public page then shows the results.
 */
@Tag("system")
class AnnualVoteRevealSystemTest : PlaywrightTestBase() {
    private fun byTestId(id: String) = TestIdLocatorHelper.byTestId(page, id)

    @Test
    fun `the reveal job reveals a due vote's top and deletes its ballots`() {
        val admin = TestHelper.registerActivateAndPromote("ADMIN")
        val voter = TestHelper.registerActivateAndPromote("MEMBER")
        val suffix = TestHelper.uniqueSuffix()
        val first = "Revealed First $suffix"
        val second = "Revealed Second $suffix"
        val year = 2100 + (suffix.takeLast(3).toInt())
        val voteId =
            TestHelper.seedRevealDueVote(
                year = year,
                voters = listOfNotNull(TestHelper.findUser(admin.username)?.id, TestHelper.findUser(voter.username)?.id),
                games = listOf(first, second),
            )
        assertThat(TestHelper.voteRows("vote_ballots", voteId)).isEqualTo(2)

        assertThat(AuthHelper.submitLogin(page, frontendUrl, admin.username, admin.password)).isEqualTo(200)
        page.navigate("$frontendUrl/management/jobs")
        byTestId("job-manager-trigger-btn").click()
        byTestId("job-trigger-dialog").waitFor()
        SelectHelper.pickFromList(page, "job-trigger-type", "Reveal due votes")
        val enqueued =
            page.awaitResponseFrom(
                control = byTestId("job-trigger-submit"),
                expected = "POST /management/jobs/enqueue",
            ) { it.request().method() == "POST" && it.url().contains("/management/jobs/enqueue") }
        assertThat(enqueued.status()).isEqualTo(200)
        val jobId = Regex("\"id\":(\\d+)").find(enqueued.text())!!.groupValues[1].toLong()

        pollForValue("the reveal job $jobId to succeed") { TestHelper.findJobExecution(jobId)?.takeIf { it.status == "SUCCESS" } }
        assertThat(TestHelper.voteRows("vote_ballots", voteId)).isZero()
        assertThat(TestHelper.voteRows("vote_placings", voteId)).isEqualTo(2)

        // The band draws its words in capitals, which innerText reads as drawn.
        page.navigate("$frontendUrl/events/ost-top-50")
        byTestId("vote-results-head").waitFor()
        assertThat(byTestId("vote-ballots").innerText()).containsIgnoringCase("2 votes")
        assertThat(page.locator("[data-testid='vote-result']").allInnerTexts()).satisfiesExactly(
            { assertThat(it).containsIgnoringCase(first) },
            { assertThat(it).containsIgnoringCase(second) },
        )
    }
}
