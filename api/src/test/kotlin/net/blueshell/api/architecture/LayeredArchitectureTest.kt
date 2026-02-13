package net.blueshell.api.architecture

import com.tngtech.archunit.library.Architectures.layeredArchitecture
import net.blueshell.api.architecture.support.ArchJUnitTestBase
import org.junit.jupiter.api.Test

/**
 * ArchUnit tests enforcing ADR-001 (Multi-Layered DDD Architecture)
 * and ADR-016 (Layer Dependency Rules).
 *
 * Dependency Rule: Dependencies point inward. Outer layers can depend on inner layers,
 * but inner layers must not depend on outer layers.
 *
 * Layer Order (outermost to innermost):
 * 1. Web Layer (Controllers, DTOs, Web Validators)
 * 2. Command Layer (Command objects - independent!)
 * 3. Application Layer (Services, Handlers, Business Validators)
 * 4. Domain Layer (optional - Rich domain models)
 * 5. Persistence Layer (Entities, Repositories)
 *
 * Infrastructure Layer (Security, ACLs) can depend on any layer (adapter pattern)
 */
class LayeredArchitectureTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {

    @Test
    fun `enforce clean architecture layering`(): Unit =
        arch("Enforce clean architecture layers with dependency rules") {
            layeredArchitecture()
                .consideringOnlyDependenciesInAnyPackage("net.blueshell.api..")

                // Define layers from outside to inside
                .layer("Web").definedBy(
                    ArchitecturePackages.WEB
                )
                .layer("Command").definedBy(
                    ArchitecturePackages.COMMAND
                )
                .layer("Application").definedBy(
                    ArchitecturePackages.APPLICATION
                )
                .layer("Domain").definedBy(
                    ArchitecturePackages.DOMAIN_MODEL,
                    ArchitecturePackages.DOMAIN_SERVICE
                )
                .layer("Persistence").definedBy(
                    ArchitecturePackages.PERSISTENCE
                )
                .layer("Infrastructure").definedBy(
                    ArchitecturePackages.INFRASTRUCTURE,
                    ArchitecturePackages.PLATFORM
                )
                .layer("Shared").definedBy(
                    ArchitecturePackages.SHARED
                )

                // Web layer can access: Command, Application, Persistence, Shared
                // Web can access persistence for direct entity returns from command handlers
                .whereLayer("Web")
                .mayOnlyAccessLayers("Web", "Command", "Application", "Persistence", "Infrastructure", "Shared")

                // Command layer is INDEPENDENT - can only access Shared
                // Critical for ADR-016: Commands must not import from Application or Web
                .whereLayer("Command")
                .mayOnlyAccessLayers("Command", "Shared")

                // Application layer can access: Command, Domain, Persistence, Shared
                .whereLayer("Application")
                .mayOnlyAccessLayers("Application", "Command", "Domain", "Persistence", "Infrastructure", "Shared")

                // Domain layer can access: Persistence (for domain services), Shared
                .whereLayer("Domain")
                .mayOnlyAccessLayers("Domain", "Persistence", "Shared")

                // Persistence layer can access: Shared and Application Query objects (ADR-015)
                .whereLayer("Persistence")
                .mayOnlyAccessLayers("Persistence", "Shared")

                // Infrastructure can access any layer (adapter pattern)
                .whereLayer("Infrastructure")
                .mayOnlyAccessLayers("Infrastructure", "Application", "Command", "Domain", "Persistence", "Shared")

                // Shared layer is innermost - no dependencies
                .whereLayer("Shared")
                .mayOnlyAccessLayers("Shared")
        }

    @Test
    fun `command objects must not depend on application layer`(): Unit =
        arch("Command objects are independent - no application layer dependencies") {
            com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.COMMAND)
                .and().haveSimpleNameEndingWith("Command")
                .and().resideOutsideOfPackages(
                    "${ArchitecturePackages.DOMAIN_EVENT}command..",  // Event domain - known issue (80% migrated)
                    "${ArchitecturePackages.DOMAIN_MEMBERSHIP}command.."  // Membership domain - validators need moving to shared
                )
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                    ArchitecturePackages.APPLICATION_VALIDATION,  // Business validators should be in shared
                    ArchitecturePackages.SERVICE
                )
                .because("ADR-016: Command objects must be independent of application services and validators")
        }

    @Test
    fun `command objects must not depend on web layer`(): Unit =
        arch("Command objects are independent - no web layer dependencies") {
            com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.COMMAND)
                .and().haveSimpleNameEndingWith("Command")
                .and().resideOutsideOfPackage("${ArchitecturePackages.DOMAIN_EVENT}command..")  // Event domain - known issue (80% migrated)
                .should().dependOnClassesThat()
                .resideInAnyPackage(ArchitecturePackages.WEB)
                .because("ADR-016: Command objects must not import web DTOs or controllers")
        }

    @Test
    fun `persistence must not depend on web layer`(): Unit =
        arch("Persistence layer independent of web concerns") {
            com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.PERSISTENCE)
                .should().dependOnClassesThat()
                .resideInAnyPackage(ArchitecturePackages.WEB)
                .because("ADR-016: Persistence layer must not know about web DTOs or controllers")
        }

    @Test
    fun `persistence must not depend on application layer`(): Unit =
        arch("Persistence layer is inner - no application dependencies except queries") {
            com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.PERSISTENCE)
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                    ArchitecturePackages.SERVICE,
                    ArchitecturePackages.COMMAND_HANDLER,
                    ArchitecturePackages.APPLICATION_VALIDATION,
                    ArchitecturePackages.LISTENER,
                    ArchitecturePackages.EVENT,
                    ArchitecturePackages.FACTORY
                )
                // Note: ArchitecturePackages.QUERY is intentionally omitted (ADR-015: Specs can use query objects)
                .because("ADR-016: Persistence can depend on query objects (ADR-015), but not services/handlers/validators")
        }
}
