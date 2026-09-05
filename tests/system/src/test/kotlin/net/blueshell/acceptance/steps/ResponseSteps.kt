package net.blueshell.acceptance.steps

import io.cucumber.java.en.Then
import net.blueshell.acceptance.AcceptanceWorld
import org.assertj.core.api.Assertions.assertThat

/**
 * What the api answered, where more than one feature says it. A refusal only one feature
 * names stays with that feature's steps, alongside whatever it asserts about the reasons.
 *
 * One step is left: #966 rewrites the last feature that reads a status code, and this
 * class goes with it.
 */
class ResponseSteps(private val world: AcceptanceWorld) {

    @Then("the request is refused")
    fun requestIsRefused() {
        assertThat(world.lastStatusCodeOrFail())
            .describedAs("expected a refusal, body: ${world.lastResponseBody}")
            .isGreaterThanOrEqualTo(400)
    }
}
