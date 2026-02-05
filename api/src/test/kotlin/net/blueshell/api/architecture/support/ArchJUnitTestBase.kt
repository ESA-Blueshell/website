package net.blueshell.api.architecture.support

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchRule
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

/**
 * Base class for ArchUnit checks executed as plain JUnit 5 tests.
 *
 * Usage:
 *   class MyArchTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {
 *     @Test fun `rule`() = arch("description") { classes().... }
 *   }
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ArchJUnitTestBase(
    vararg packages: String,
    private val importOptions: List<ImportOption> = listOf(
        ImportOption.DoNotIncludeTests(),
        DoNotIncludeTestSupport(),
        DoNotIncludeFactory()
    )
) {
    private val packagesToScan = packages.toList()

    protected lateinit var importedClasses: JavaClasses
        private set

    @BeforeAll
    fun importOnce() {
        val importer = importOptions.fold(ClassFileImporter()) { acc, opt -> acc.withImportOption(opt) }
        importedClasses = importer.importPackages(*packagesToScan.toTypedArray())
    }

    protected inline fun arch(description: String, crossinline rule: () -> ArchRule) {
        rule().`as`(description).check(importedClasses)
    }
}
