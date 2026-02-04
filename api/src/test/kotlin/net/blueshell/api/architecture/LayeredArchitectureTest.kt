package net.blueshell.api.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import net.blueshell.api.testsupport.DoNotIncludeFactory
import net.blueshell.api.testsupport.DoNotIncludeTestSupport

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

            .whereLayer("Controllers")
            .mayOnlyAccessLayers("Controllers", "Mappers", "Validation", "Services", "Model", "DTO")
            .whereLayer("Mappers").mayOnlyAccessLayers("Mappers", "Model", "DTO")
            .whereLayer("Validation").mayOnlyAccessLayers("Validation", "DTO", "Model", "Repositories", "Services")
            .whereLayer("Services").mayOnlyAccessLayers("Services", "Repositories", "Model", "Mappers")
            .whereLayer("Repositories").mayOnlyAccessLayers("Repositories", "Model")
            .whereLayer("Model").mayOnlyAccessLayers("Model")
            .whereLayer("DTO").mayOnlyAccessLayers("DTO")
}
