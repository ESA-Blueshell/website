package net.blueshell.api.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import net.blueshell.api.architecture.support.DoNotIncludeFactory
import net.blueshell.api.architecture.support.DoNotIncludeTestSupport

/**
 * High-level layering rule (best practice).
 *
 * Note: If your codebase is still in transition, this may be intentionally strict.
 */
@AnalyzeClasses(
    packages = [ArchitecturePackages.ROOT],
    importOptions = [
        ImportOption.DoNotIncludeTests::class,
        DoNotIncludeTestSupport::class,
        DoNotIncludeFactory::class
    ]
)
class LayeredArchitectureTest {

    @ArchTest
    val enforceLayering: ArchRule =
        layeredArchitecture()
            .consideringAllDependencies()

            .layer("Controllers").definedBy(ArchitecturePackages.CONTROLLER)
            .layer("Mappers").definedBy(ArchitecturePackages.MAPPER)
            .layer("Validation").definedBy(ArchitecturePackages.VALIDATION)
            .layer("Services").definedBy(ArchitecturePackages.SERVICE)
            .layer("Repositories").definedBy(ArchitecturePackages.REPOSITORY)
            .layer("Model").definedBy(ArchitecturePackages.MODEL)
            .layer("DTO").definedBy(ArchitecturePackages.DTO)
            .layer("Common").definedBy(ArchitecturePackages.COMMON)

            .whereLayer("Controllers")
            .mayOnlyAccessLayers("Controllers", "Mappers", "Validation", "Services", "Model", "DTO", "Common")
            .whereLayer("Mappers").mayOnlyAccessLayers("Mappers", "Model", "DTO", "Common")
            .whereLayer("Validation")
            .mayOnlyAccessLayers("Validation", "DTO", "Model", "Repositories", "Services", "Common")
            .whereLayer("Services").mayOnlyAccessLayers("Services", "Repositories", "Model", "Mappers", "Common")
            .whereLayer("Repositories").mayOnlyAccessLayers("Repositories", "Model", "Common")
            .whereLayer("Model").mayOnlyAccessLayers("Model", "Common")
            .whereLayer("DTO").mayOnlyAccessLayers("DTO", "Common")
            .whereLayer("Common").mayOnlyAccessLayers("Common")
}
