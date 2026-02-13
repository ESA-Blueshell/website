package net.blueshell.api.architecture.support

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.core.importer.Location

/**
 * ArchUnit import option excluding test-support utilities from the application scan.
 */
class DoNotIncludeTestSupport : ImportOption {
    override fun includes(location: Location): Boolean =
        !location.contains("testsupport")
}
