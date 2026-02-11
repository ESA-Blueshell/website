package net.blueshell.api.architecture

import com.tngtech.archunit.library.Architectures.layeredArchitecture
import net.blueshell.api.architecture.support.ArchJUnitTestBase
import org.junit.jupiter.api.Test

class LayeredArchitectureTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {

    @Test
    fun `enforce layering`(): Unit =
        arch("Enforce application layering") {
            layeredArchitecture()
                .consideringOnlyDependenciesInAnyPackage("net.blueshell.api..")

                .layer("Controllers").definedBy(ArchitecturePackages.CONTROLLER)
                .layer("Validation").definedBy(ArchitecturePackages.VALIDATION)
                .layer("Services").definedBy(ArchitecturePackages.SERVICE)
                .layer("Repositories").definedBy(ArchitecturePackages.REPOSITORY)
                .layer("Model").definedBy(ArchitecturePackages.MODEL, ArchitecturePackages.PERSISTENCE)
                .layer("DTO").definedBy(ArchitecturePackages.DTO)
                .layer("Common").definedBy(ArchitecturePackages.COMMON)

                .whereLayer("Controllers")
                .mayOnlyAccessLayers("Controllers", "Services", "Validation", "DTO", "Model", "Common")
                .whereLayer("Validation")
                .mayOnlyAccessLayers("Validation", "DTO", "Model", "Repositories", "Services", "Common")
                .whereLayer("Services")
                .mayOnlyAccessLayers("Services", "Repositories", "Model", "Common", "Validation")
                .whereLayer("Repositories")
                .mayOnlyAccessLayers("Repositories", "Model", "Common")
                .whereLayer("Model")
                .mayOnlyAccessLayers("Model", "DTO", "Common")
                .whereLayer("DTO")
                .mayOnlyAccessLayers("DTO", "Model", "Common")
                .whereLayer("Common")
                .mayOnlyAccessLayers("Common")
        }
}
