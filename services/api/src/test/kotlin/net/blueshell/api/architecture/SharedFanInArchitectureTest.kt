package net.blueshell.api.architecture

import net.blueshell.api.architecture.support.ArchJUnitTestBase
import net.blueshell.api.architecture.support.ArchModules
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Architecture ADR-003 placement rule 2: *only one module uses it → it does not
 * belong in `shared`*. Fan-in is countable, so the rule is a test rather than an
 * argument.
 *
 * A package reached by one module is that module's code filed in the wrong place, and one
 * reached by none is dead. Pinned at the fan-in measured when the rule landed rather than at the
 * ideal: a package below the threshold is named in [BELOW_THRESHOLD] with its reason, so the
 * rule still bites for every other package and every one added later. Thirteen of the fourteen
 * packages under `shared` clear it, from `shared/dto/bulk` at three consumers to `shared/enums`
 * at 22.
 */
class SharedFanInArchitectureTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {

    private companion object {
        /** A shared package needs consumers in more than one module. */
        const val MINIMUM_CONSUMER_MODULES = 2

        /**
         * Packages below the threshold when this rule landed. Each is a
         * candidate for moving into its single consumer; until then the entry
         * keeps the rest of the rule enforceable.
         */
        val BELOW_THRESHOLD = mapOf(
            // Dirty-tracking support. Its other reader, HibernateDirtyTrackingConfig,
            // is application-root wiring under ADR-003 rule 6 rather than a module,
            // so `survey` is the only module that reaches it.
            "shared/hibernate" to 1,
        )
    }

    @Test
    fun `every shared package is reached by more than one module`() {
        val fanIn = measureFanIn()

        val offenders = fanIn
            .filterKeys { it !in BELOW_THRESHOLD }
            .filterValues { it.size < MINIMUM_CONSUMER_MODULES }
            .map { (pkg, modules) -> "$pkg reached by ${modules.size} module(s): ${modules.sorted()}" }
            .sorted()

        assertThat(offenders)
            .describedAs(
                "architecture ADR-003 rule 2: a package in shared serves more than one module. " +
                    "Move a single-consumer package into its consumer, delete a zero-consumer one, " +
                    "or add it to BELOW_THRESHOLD with the reason",
            )
            .isEmpty()
    }

    @Test
    fun `no pinned package stays pinned once its fan-in recovers`() {
        val fanIn = measureFanIn()

        val stale = BELOW_THRESHOLD.keys
            .filter { (fanIn[it]?.size ?: 0) >= MINIMUM_CONSUMER_MODULES }
            .sorted()

        assertThat(stale)
            .describedAs(
                "these packages now clear the fan-in threshold — drop them from BELOW_THRESHOLD " +
                    "so the ratchet cannot slip back",
            )
            .isEmpty()
    }

    @Test
    fun `every pinned package still exists`() {
        val fanIn = measureFanIn()

        val vanished = BELOW_THRESHOLD.keys.filter { it !in fanIn }.sorted()

        assertThat(vanished)
            .describedAs("these packages are gone — drop them from BELOW_THRESHOLD")
            .isEmpty()
    }

    /**
     * Every package under `shared` that holds types, mapped to the modules that
     * reach into it. A package with no consumer still appears, with an empty set,
     * so zero-consumer packages fail rather than disappear from the measurement.
     * Reaches from one shared package into another are same-module and do not count.
     */
    private fun measureFanIn(): Map<String, Set<String>> {
        val fanIn = importedClasses
            .mapNotNull { ArchModules.sharedPackageOf(it) }
            .associateWith { mutableSetOf<String>() }

        importedClasses.forEach { origin ->
            val originModule = ArchModules.moduleOf(origin) ?: return@forEach
            if (originModule == "shared") return@forEach
            origin.directDependenciesFromSelf
                .mapNotNull { ArchModules.sharedPackageOf(it.targetClass) }
                .forEach { fanIn[it]?.add(originModule) }
        }

        return fanIn
    }
}
