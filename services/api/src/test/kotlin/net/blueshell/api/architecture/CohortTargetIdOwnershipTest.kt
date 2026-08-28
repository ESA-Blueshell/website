package net.blueshell.api.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The cohort external target id now lives solely on `cohort.external_id`
 * (owned by `CohortTargetIds`). With the V77 compatibility window closed (V79
 * final backfill), no cohort code should reach into the legacy
 * `external_id_mapping(aggregate_type='COHORT')` via `COHORT_AGGREGATE` at all.
 *
 * `COHORT_AGGREGATE` is a `const val`, so a reference is inlined at compile
 * time and ArchUnit cannot see it as a dependency — hence a source scan.
 */
class CohortTargetIdOwnershipTest {

    @Test
    fun `no cohort code references COHORT_AGGREGATE`() {
        val root = listOf(
            File("src/main/kotlin/net/blueshell/api/cohort"),
            File("services/api/src/main/kotlin/net/blueshell/api/cohort"),
        ).firstOrNull { it.isDirectory }
            ?: error("cohort source root not found from ${File("").absolutePath}")

        val referencingFiles = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("COHORT_AGGREGATE") }
            .map { it.name }
            .toList()

        assertThat(referencingFiles).isEmpty()
    }
}
