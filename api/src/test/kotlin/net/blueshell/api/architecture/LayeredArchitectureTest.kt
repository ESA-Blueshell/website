package net.blueshell.api.architecture

import com.tngtech.archunit.library.Architectures.layeredArchitecture
import net.blueshell.api.architecture.support.ArchJUnitTestBase
import org.junit.jupiter.api.Test

class LayeredArchitectureTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {

    @Test
    fun `enforce layering`(): Unit =
        arch("Enforce application layering") {
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
                .mayOnlyAccessLayers("Controllers", "Services", "Validation", "Mappers", "DTO", "Common")
                .whereLayer("Mappers")
                .mayOnlyAccessLayers("Mappers", "Model", "DTO", "Common")
                .whereLayer("Validation")
                .mayOnlyAccessLayers("Validation", "DTO", "Model", "Repositories", "Services", "Common")
                .whereLayer("Services")
                .mayOnlyAccessLayers("Services", "Repositories", "Model", "Mappers", "Common", "Validation")
                .whereLayer("Repositories")
                .mayOnlyAccessLayers("Repositories", "Model", "Common")
                .whereLayer("Model")
                .mayOnlyAccessLayers("Model", "Common")
                .whereLayer("DTO")
                .mayOnlyAccessLayers("DTO", "Common")
                .whereLayer("Common")
                .mayOnlyAccessLayers("Common")
        }
}
