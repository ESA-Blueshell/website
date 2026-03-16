package net.blueshell.api.architecture.support

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.core.importer.Location

/**
 * ArchUnit import option excluding Spring AOT-generated BeanDefinitions classes from the scan.
 * Spring AOT generates classes like `SomeHandler__TestContext007_BeanDefinitions` that end up
 * in the same packages as application classes, causing false architecture violations.
 */
class DoNotIncludeAotGenerated : ImportOption {
    override fun includes(location: Location): Boolean =
        !location.contains("__") && !location.contains("BeanDefinitions")
}
