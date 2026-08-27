package net.blueshell.acceptance.steps

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.assertj.core.api.Assertions.assertThat

// Runs without the stack, so a job that filtered every feature out cannot look
// identical to a job that ran the suite.
class HarnessSelfCheckSteps {
    private var wired = false

    @Given("the acceptance harness is wired")
    fun theHarnessIsWired() {
        wired = true
    }

    @Then("it can execute a scenario")
    fun itCanExecuteAScenario() {
        assertThat(wired).isTrue()
    }
}
