package net.blueshell.api.platform.integration.mock

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MockTargetStrategyTest {
    @Test
    fun `a created target's path starts at the system, and takes its folder when it has one`() {
        val strategy = MockTargetStrategy()

        assertThat(strategy.create("Guests", "Committees").path).containsExactly("Brevo", "Committees")
        assertThat(strategy.create("Members", " ").path).containsExactly("Brevo")
    }
}
