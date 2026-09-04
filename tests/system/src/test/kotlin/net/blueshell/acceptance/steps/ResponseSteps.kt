package net.blueshell.acceptance.steps

import io.cucumber.java.en.Then
import net.blueshell.acceptance.AcceptanceWorld
import org.assertj.core.api.Assertions.assertThat

/**
 * What the api answered, in the words the feature files still use for it.
 *
 * These steps are the transport vocabulary #963 is removing from the feature files. They
 * live together so the rewrites behind it can see the whole of what disappears, rather
 * than finding half of it inside a feature's own step class.
 */
class ResponseSteps(private val world: AcceptanceWorld) {

    @Then("the request succeeds")
    fun requestSucceeds() {
        assertThat(world.lastStatusCodeOrFail()).isEqualTo(200)
    }

    @Then("the request is refused")
    fun requestIsRefused() {
        assertThat(world.lastStatusCodeOrFail())
            .describedAs("expected a refusal, body: ${world.lastResponseBody}")
            .isGreaterThanOrEqualTo(400)
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
        val errors = world.refusalErrors()
        assertThat(errors.map { it["code"] }).contains(code)
        assertThat(errors.single { it["code"] == code }["field"]).isEqualTo(field)
    }

    @Then("the refusal reports both {string} and {string}")
    fun refusalReportsBoth(first: String, second: String) {
        assertThat(world.refusalErrors().map { it["code"] }).contains(first, second)
    }
}
