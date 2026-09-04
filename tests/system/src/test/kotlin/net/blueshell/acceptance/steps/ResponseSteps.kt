package net.blueshell.acceptance.steps

import io.cucumber.java.en.Then
import net.blueshell.acceptance.AcceptanceWorld
import org.assertj.core.api.Assertions.assertThat

/**
 * What the api answered, where more than one feature says it. A refusal only one feature
 * names stays with that feature's steps, alongside whatever it asserts about the reasons.
 */
class ResponseSteps(private val world: AcceptanceWorld) {

    @Then("the request is refused")
    fun requestIsRefused() {
        assertThat(world.lastStatusCodeOrFail())
            .describedAs("expected a refusal, body: ${world.lastResponseBody}")
            .isGreaterThanOrEqualTo(400)
    }

    @Then("the request is refused as invalid")
    fun requestRefusedAsInvalid() {
        assertThat(world.lastStatusCodeOrFail()).isEqualTo(400)
    }

    @Then("the request is forbidden")
    fun requestForbidden() {
        assertThat(world.lastStatusCodeOrFail()).isEqualTo(403)
    }
}
