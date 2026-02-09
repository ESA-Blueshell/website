package net.blueshell.api.architecture.support

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.core.importer.Location

/**
 * ArchUnit import option excluding test source outputs from the application scan.
 */
class DoNotIncludeTestSources : ImportOption {
    private val excludedMarkers = listOf(
        "/src/test/",
        "/src/testFixtures/",
        "/src/integrationTest/",
        "/build/classes/kotlin/test/",
        "/build/classes/java/test/",
        "/build/classes/kotlin/testFixtures/",
        "/build/resources/test/",
        "/target/test-classes/",
        "/test-classes/"
    )

    override fun includes(location: Location): Boolean =
        excludedMarkers.none { marker -> location.contains(marker) }
}
