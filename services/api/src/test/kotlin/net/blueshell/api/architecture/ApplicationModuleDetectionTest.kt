package net.blueshell.api.architecture

import net.blueshell.api.ApiApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

/**
 * The module packages are nested, so detection is the part that can silently do nothing:
 * a strategy that nominates no package produces an empty `ApplicationModules` and every
 * later check passes vacuously. This pins the twenty modules by name.
 *
 * Deliberately does not call `verify()` — the cycles are not broken yet.
 */
class ApplicationModuleDetectionTest {

    private val modules = ApplicationModules.of(ApiApplication::class.java)

    @Test
    fun `every module is detected under its flat name`() {
        assertThat(modules.map { it.identifier.toString() })
            .containsExactlyInAnyOrder(
                "user", "event", "auth", "contribution", "survey", "committee",
                "board", "esports", "file", "blog", "telemetry", "sponsor",
                "cohort", "jobs", "contact", "email", "sync", "oidc",
                "security", "shared",
            )
    }

    @Test
    fun `only security and shared are open`() {
        assertThat(modules.filter { it.isOpen }.map { it.identifier.toString() })
            .containsExactlyInAnyOrder("security", "shared")
    }
}
