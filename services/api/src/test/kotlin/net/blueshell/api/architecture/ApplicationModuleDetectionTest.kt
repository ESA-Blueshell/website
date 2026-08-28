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
 * `verify()` runs here too: it is the boundary check itself, and it needs the detection above
 * to have found something before it means anything.
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

    /**
     * Every module declares a closed `allowedDependencies` whitelist, and each entry names a
     * module and one of its named interfaces. A misspelt module id or an interface that no
     * package publishes throws here — which is the only signal until `verify()` is switched on,
     * because a whitelist is resolved lazily during verification and not at detection.
     */
    @Test
    fun `every declared dependency resolves to a named interface that exists`() {
        val unresolvable = modules.mapNotNull { module ->
            runCatching { module.getAllowedDependencies(modules) }
                .exceptionOrNull()
                ?.let { "${module.identifier}: ${it.message}" }
        }

        assertThat(unresolvable).isEmpty()
    }

    /**
     * The boundary check. A module may reach another only through a named interface its
     * `allowedDependencies` lists, no closed module may take part in a cycle, and nothing outside
     * a module may reach a type the module does not publish. Static analysis over the compiled
     * classes — no Spring context and no database, per testing ADR-001.
     */
    @Test
    fun `module boundaries hold`() {
        modules.verify()
    }

    @Test
    fun `no module is left open to every other module`() {
        val open = modules
            .filter { it.getAllowedDependencies(modules).isEmpty }
            .map { it.identifier.toString() }

        assertThat(open)
            .describedAs(
                "a module without allowedDependencies may reach any other module's entities, " +
                    "which is what the two named interfaces exist to prevent",
            )
            .isEmpty()
    }
}
