package net.blueshell.acceptance.steps

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import net.blueshell.acceptance.AcceptanceApi
import net.blueshell.acceptance.AcceptanceWorld
import net.blueshell.systemtests.TestHelper
import org.assertj.core.api.Assertions.assertThat

class MembershipSteps(private val world: AcceptanceWorld) {

    @Given("they have completed their member profile")
    fun theyHaveCompletedTheirMemberProfile() {
        TestHelper.attachMemberProfile(world.applicant())
    }

    @Given("they have an address on file")
    fun theyHaveAnAddressOnFile() {
        TestHelper.attachAddress(
            user = world.applicant(),
            city = "Enschede",
            street = "Drienerlolaan",
            houseNumber = "5",
            zipCode = "7522NB",
        )
    }

    // One step for every omission, so a new precondition is a table row.
    @Given("their application is missing {string}")
    fun theirApplicationIsMissing(missing: String) {
        when (missing) {
            "their member profile" -> theyHaveAnAddressOnFile()
            "their address" -> theyHaveCompletedTheirMemberProfile()
            else -> error(
                "Unknown missing precondition \"$missing\". " +
                    "Add it to MembershipSteps.theirApplicationIsMissing.",
            )
        }
    }

    @Given("they have submitted their membership application")
    @When("they submit their membership application")
    @When("they submit their membership application again")
    fun theySubmitTheirMembershipApplication() {
        val applicant = world.applicant()
        world.confirmationEmailsBeforeAction = AcceptanceApi.confirmationEmailCount(applicant.email)
        val cookies = AcceptanceApi.signIn(applicant)
        val response = AcceptanceApi.submitMembershipApplication(cookies)
        world.recordResponse(response.statusCode, response.asString())
    }

    @Then("they are a member")
    fun theyAreAMember() {
        assertThat(world.lastStatusCodeOrFail())
            .describedAs("membership application response: ${world.lastResponseBody}")
            .isEqualTo(201)
        assertThat(TestHelper.hasActiveMembership(world.applicant().username))
            .describedAs("active membership for ${world.applicant().username}")
            .isTrue()
    }

    @Then("they are not a member")
    fun theyAreNotAMember() {
        assertThat(TestHelper.hasActiveMembership(world.applicant().username))
            .describedAs("active membership for ${world.applicant().username}")
            .isFalse()
    }

    @Then("their application is refused")
    fun theirApplicationIsRefused() {
        assertThat(world.lastStatusCodeOrFail())
            .describedAs("membership application should have been refused, body: ${world.lastResponseBody}")
            .isNotEqualTo(201)
    }

    @Then("they hold the MEMBER role")
    fun theyHoldTheMemberRole() {
        assertThat(TestHelper.findRoles(world.applicant().username))
            .describedAs("roles for ${world.applicant().username}")
            .contains("MEMBER")
    }

    @Then("they have exactly one membership")
    fun theyHaveExactlyOneMembership() {
        assertThat(TestHelper.hasActiveMembership(world.applicant().username))
            .describedAs("active membership for ${world.applicant().username}")
            .isTrue()
        assertThat(membershipCount(world.applicantId()))
            .describedAs("membership rows for ${world.applicant().username}")
            .isEqualTo(1)
    }

    private fun membershipCount(userId: Long): Int =
        TestHelper.membershipCountForUser(userId)
}
