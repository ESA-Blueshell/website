package net.blueshell.api.architecture.support

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.core.importer.Location

/**
 * ArchUnit import option excluding Spring AOT-generated classes from the scan.
 *
 * Spring AOT generates two kinds of classes that pollute ArchUnit analysis:
 * - `SomeHandler__TestContext007_BeanDefinitions` - AOT bean definition descriptors
 * - `SomeHandler$$SpringCGLIB$$0` - statically-generated CGLIB proxies
 *
 * Both end up in the same packages as application classes, causing false architecture violations.
 */
class DoNotIncludeAotGenerated : ImportOption {
    override fun includes(location: Location): Boolean =
        !location.contains("__") &&
        !location.contains("BeanDefinitions") &&
        !location.contains("\$\$SpringCGLIB\$\$")
}
