package net.blueshell.api.testsupport

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.core.importer.Location

/**
 * ArchUnit import option excluding factories from the application scan.
 */
class DoNotIncludeFactory : ImportOption {
    override fun includes(location: Location): Boolean =
        !location.contains("factory")
}
