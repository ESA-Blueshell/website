package net.blueshell.acceptance.steps

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.restassured.path.json.JsonPath
import io.restassured.response.Response
import net.blueshell.acceptance.AcceptanceWorld
import net.blueshell.systemtests.TestEnvironment
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat

/**
 * Steps for docs/flows/recovery-emails. Drives the preview and resend endpoints over HTTP
 * and reads tokens straight from the database, so a scenario asserts what was issued
 * rather than what the response claimed.
 */
class RecoveryEmailSteps(private val world: AcceptanceWorld) {

    private var cookies: TestHelper.LoginCookies? = null
    private val previews = mutableListOf<String>()
    private var emailsBefore: Int = 0

    // Signing up already issues a link and sends its email, so "nothing happened" is a
    // comparison against what the account came with, not a count of zero.
    private var linksBefore: Map<String, Int> = emptyMap()

    @Given("a board member signed in to the recovery manager")
    fun aBoardMemberSignedIn() {
        val board = TestHelper.registerActivateAndPromote("BOARD")
        world.createdUsernames += board.username
        cookies = TestHelper.login(board)
    }

    @Given("a member signed in who may not email that account")
    fun aMemberWhoMayNotEmail() {
        val member = TestHelper.registerActivateAndPromote("MEMBER")
        world.createdUsernames += member.username
        cookies = TestHelper.login(member)
    }

    @Given("an account that has not been activated")
    fun anInactiveAccount() {
        // register leaves the account disabled, which is the state both activation emails address.
        world.rememberApplicant(TestHelper.register())
        recordBaseline()
    }

    @Given("an account that has been activated")
    fun anActiveAccount() {
        world.rememberApplicant(TestHelper.registerAndActivate())
        recordBaseline()
    }

    @When("they preview the {string} email")
    fun previewEmail(purpose: String) {
        val response = get("/recovery/users/${world.applicantId()}/email-preview?purpose=$purpose")
        world.recordResponse(response.statusCode, response.asString())
        if (response.statusCode == 200) previews += response.asString()
    }

    @When("they resend the {string} email")
    fun resendEmail(purpose: String) {
        val response = TestHelper.givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .cookie(TestEnvironment.authCookieName, requireNotNull(cookies).auth)
            .`when`()
            .post("/recovery/users/${world.applicantId()}/resend/recovery?purpose=$purpose")
        world.recordResponse(response.statusCode, response.asString())
    }

    // The bulk endpoints answer 200, these answer 204, so "it worked" is stated here rather
    // than borrowed from a feature whose success looks different.
    @Then("the request is refused as invalid")
    fun requestRefusedAsInvalid() {
        assertThat(world.lastStatusCodeOrFail()).isEqualTo(400)
    }

    @Then("the request is forbidden")
    fun requestForbidden() {
        assertThat(world.lastStatusCodeOrFail()).isEqualTo(403)
    }

    @Then("the send is accepted")
    fun sendIsAccepted() {
        assertThat(world.lastStatusCodeOrFail()).isEqualTo(204)
    }

    @Then("the preview is returned")
    fun previewIsReturned() {
        assertThat(world.lastStatusCodeOrFail()).isEqualTo(200)
        assertThat(body().getString("html")).isNotBlank()
        assertThat(body().getString("subject")).isNotBlank()
    }

    @Then("the preview is addressed to that account")
    fun previewIsAddressedToTheAccount() {
        assertThat(body().getString("recipientEmail")).isEqualTo(world.applicant().email)
        assertThat(body().getString("html")).contains(body().getString("recipientName"))
    }

    @Then("the preview link carries no token")
    fun previewLinkCarriesNoToken() {
        val placeholder = body().getString("linkPlaceholder")
        assertThat(placeholder).describedAs("the stand-in for the token").isNotBlank()
        // The link the reader sees is the placeholder, so there is no credential on the page.
        assertThat(body().getString("html")).contains(placeholder)
    }

    @Then("the two previews differ")
    fun theTwoPreviewsDiffer() {
        assertThat(previews).describedAs("previews rendered in this scenario").hasSize(2)
        assertThat(previews[0]).isNotEqualTo(previews[1])
    }

    @Then("it has no outstanding {string} link")
    fun noOutstandingLink(type: String) {
        assertThat(TestHelper.outstandingRecoveryLinks(world.applicant().username, type))
            .describedAs("outstanding $type links")
            .isZero()
    }

    @Then("the account has {int} outstanding {string} link")
    fun outstandingLinkCount(expected: Int, type: String) = assertOutstanding(expected, type)

    @Given("it has {int} outstanding {string} link")
    fun preconditionOutstandingLinkCount(expected: Int, type: String) = assertOutstanding(expected, type)

    @Then("no link was issued")
    fun noLinkWasIssued() {
        ACTIVATION_TYPES.forEach { type ->
            assertThat(TestHelper.outstandingRecoveryLinks(world.applicant().username, type))
                .describedAs("outstanding $type links, against the $type links the account arrived with")
                .isEqualTo(linksBefore.getValue(type))
        }
    }

    private fun assertOutstanding(expected: Int, type: String) {
        assertThat(TestHelper.outstandingRecoveryLinks(world.applicant().username, type))
            .describedAs("outstanding $type links")
            .isEqualTo(expected)
    }

    @Then("no email was sent")
    fun noEmailWasSent() {
        assertThat(emailCount())
            .describedAs("emails to ${world.applicant().email} after the action")
            .isEqualTo(emailsBefore)
    }

    @Then("an email was sent")
    fun anEmailWasSent() {
        net.blueshell.systemtests.pollFor("an email to ${world.applicant().email} beyond the $emailsBefore already sent") {
            emailCount() > emailsBefore
        }
    }

    private fun recordBaseline() {
        emailsBefore = emailCount()
        linksBefore = ACTIVATION_TYPES.associateWith {
            TestHelper.outstandingRecoveryLinks(world.applicant().username, it)
        }
    }

    private fun emailCount(): Int = TestHelper.findEmails(recipient = world.applicant().email).size

    private fun body(): JsonPath = JsonPath.from(requireNotNull(world.lastResponseBody))

    private fun get(path: String): Response =
        TestHelper.givenCsrfApi()
            .baseUri(TestEnvironment.apiUrl)
            .cookie(TestEnvironment.authCookieName, requireNotNull(cookies).auth)
            .`when`()
            .get(path)

    private companion object {
        val ACTIVATION_TYPES = listOf("USER_ACTIVATION", "MEMBER_ACTIVATION")
    }
}
