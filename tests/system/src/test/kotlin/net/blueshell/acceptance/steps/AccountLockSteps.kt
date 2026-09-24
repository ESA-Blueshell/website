package net.blueshell.acceptance.steps

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import net.blueshell.acceptance.AcceptanceApi
import net.blueshell.acceptance.AcceptanceWorld
import net.blueshell.acceptance.Inbox
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat

class AccountLockSteps(
    private val world: AcceptanceWorld,
) {
    private var lockToken: String? = null

    @Given("a member who has changed their password")
    fun aMemberWhoHasChangedTheirPassword() {
        val member = TestHelper.registerAndActivate()
        world.rememberApplicant(member)
        val response = AcceptanceApi.changePassword(member, NEW_PASSWORD)
        world.recordResponse(response.statusCode, response.asString())
        world.rememberApplicant(member.copy(password = NEW_PASSWORD))
    }

    @Then("they receive a security notification with a lock link")
    fun theyReceiveASecurityNotificationWithALockLink() {
        val email = Inbox.await(world.applicant().email, "Security notification", world.lastStatusCode, world.lastResponseBody)
        lockToken = AcceptanceApi.linkToken(email.htmlContent, "account/lock")
    }

    @When("they follow the lock link")
    fun theyFollowTheLockLink() {
        val response = AcceptanceApi.followLockLink(lockToken ?: error("No lock link was received"))
        world.recordResponse(response.statusCode, response.asString())
    }

    @Then("signing in is refused because the account is locked")
    fun signingInIsRefusedBecauseTheAccountIsLocked() {
        val response = AcceptanceApi.attemptSignIn(world.applicant())
        assertThat(response.statusCode).isGreaterThanOrEqualTo(400)
        assertThat(response.asString()).contains("AccountLocked")
    }

    @Given("a member whose account is locked")
    fun aMemberWhoseAccountIsLocked() {
        aMemberWhoHasChangedTheirPassword()
        theyReceiveASecurityNotificationWithALockLink()
        theyFollowTheLockLink()
    }

    @When("an admin unlocks it, giving a reason")
    fun anAdminUnlocksItGivingAReason() {
        val admin = TestHelper.registerActivateAndPromote("ADMIN")
        world.createdUsernames += admin.username
        val response = AcceptanceApi.unlock(TestHelper.login(admin).auth, world.applicantId(), "heard from them in person")
        world.recordResponse(response.statusCode, response.asString())
    }

    @Then("they receive a password reset email")
    fun theyReceiveAPasswordResetEmail() {
        Inbox.await(world.applicant().email, "Reset Your Blueshell Account Password", world.lastStatusCode, world.lastResponseBody)
    }

    private companion object {
        const val NEW_PASSWORD = "Another123!pass"
    }
}
