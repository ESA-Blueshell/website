package net.blueshell.api.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * `CohortTargetIds` is the single owner of a cohort's external target id.
 * Other cohort code resolves the id through it rather than reaching into the
 * legacy `external_id_mapping(aggregate_type='COHORT')` directly.
 *
 * `COHORT_AGGREGATE` is a `const val`, so a reference is inlined at compile
 * time and ArchUnit cannot see it as a dependency — hence a source scan.
 * Item 8 removes the legacy fallback; until then this stops the indirection
 * leaking back into other cohort classes.
 */
class CohortTargetIdOwnershipTest {

    @Test
    fun `only CohortTargetIds references COHORT_AGGREGATE in cohort code`() {
        val root = listOf(
            File("src/main/kotlin/net/blueshell/api/platform/integration/cohort"),
            File("services/api/src/main/kotlin/net/blueshell/api/platform/integration/cohort"),
        ).firstOrNull { it.isDirectory }
            ?: error("cohort source root not found from ${File("").absolutePath}")

        val referencingFiles = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("COHORT_AGGREGATE") }
            .map { it.name }
            .toList()

        assertThat(referencingFiles).containsExactly("CohortTargetIds.kt")
    }
}
