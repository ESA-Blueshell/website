package net.blueshell.acceptance

import io.cucumber.java.After
import io.cucumber.java.Scenario
import net.blueshell.systemtests.TestHelper

// Erasing what each scenario created keeps scenarios order-independent.
class Hooks(private val world: AcceptanceWorld) {

    @After
    fun eraseAccountsCreatedByThisScenario(scenario: Scenario) {
        world.createdUsernames.forEach { username ->
            // Logged, not thrown: must not mask the scenario's own result.
            runCatching { TestHelper.eraseUser(username) }
                .onFailure { scenario.log("Could not erase $username: ${it.message}") }
        }
    }
}
