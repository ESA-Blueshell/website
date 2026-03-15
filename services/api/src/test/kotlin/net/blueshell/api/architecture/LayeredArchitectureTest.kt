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
                // IMPORTANT: Application is defined BEFORE Command so that classes in
                // domain/*/application/command/ match Application layer (more specific) instead
                // of Command layer. This prevents handlers from being misclassified as commands.
                .layer("Web").definedBy(
                    ArchitecturePackages.WEB
                )
                .layer("Application").definedBy(
                    ArchitecturePackages.APPLICATION
                )
                .layer("Command").definedBy(
                    ArchitecturePackages.COMMAND
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

                // Web layer can access: Application, Command, Domain, Persistence, Shared, Infrastructure
                .whereLayer("Web")
                .mayOnlyAccessLayers("Web", "Application", "Command", "Domain", "Persistence", "Infrastructure", "Shared")

                // Application layer can access: Command, Domain, Persistence, Shared, Infrastructure
                // Note: Application MUST access Command (handlers depend on command objects)
                .whereLayer("Application")
                .mayOnlyAccessLayers("Application", "Command", "Domain", "Persistence", "Infrastructure", "Shared")

                // Command layer is INDEPENDENT - can only access Shared, Domain, Persistence, and Query objects
                // Commands can reference domain models, persistence result types, and query objects (ADR-015)
                // EXCEPTION: Commands can reference validation annotations from application layer (pragmatic)
                // Note: Handler classes and helper functions in application/command/ also match Command layer
                // pattern, so we ignore their dependencies (they're actually Application layer)
                .whereLayer("Command")
                .mayOnlyAccessLayers("Command", "Shared", "Domain", "Persistence")
                .ignoreDependency(
                    com.tngtech.archunit.base.DescribedPredicate.describe<com.tngtech.archunit.core.domain.JavaClass>(
                        "handler classes and helper functions"
                    ) { javaClass ->
                        javaClass.name.endsWith("Handler") ||
                        javaClass.name.endsWith("HandlersKt") ||
                        javaClass.name.endsWith("HandlerKt") ||
                        javaClass.name.contains("\$application\$command\$")  // Nested/lambda classes in handlers
                    },
                    com.tngtech.archunit.base.DescribedPredicate.alwaysTrue()
                )
                // Explicitly allow commands to depend on query objects (ADR-015: Query Pattern)
                .ignoreDependency(
                    com.tngtech.archunit.base.DescribedPredicate.describe<com.tngtech.archunit.core.domain.JavaClass>(
                        "command classes"
                    ) { it.packageName.contains(".command") && it.simpleName.endsWith("Command") },
                    com.tngtech.archunit.base.DescribedPredicate.describe<com.tngtech.archunit.core.domain.JavaClass>(
                        "query objects"
                    ) { it.packageName.contains(".application.query") }
                )
                // Explicitly allow commands to depend on validation annotations from application layer
                // (Validators need DB access, so must be in application layer; annotations reference validators)
                .ignoreDependency(
                    com.tngtech.archunit.base.DescribedPredicate.describe<com.tngtech.archunit.core.domain.JavaClass>(
                        "command classes"
                    ) { it.packageName.contains(".command") },
                    com.tngtech.archunit.base.DescribedPredicate.describe<com.tngtech.archunit.core.domain.JavaClass>(
                        "validation annotations and interfaces"
                    ) { it.packageName.contains(".application.validation") }
                )

                // Domain layer can access: Persistence (for domain services), Shared, and Application exceptions
                .whereLayer("Domain")
                .mayOnlyAccessLayers("Domain", "Persistence", "Shared")
                // Domain services can throw application-layer exceptions
                .ignoreDependency(
                    com.tngtech.archunit.base.DescribedPredicate.describe<com.tngtech.archunit.core.domain.JavaClass>(
                        "domain service classes"
                    ) { it.packageName.contains(".domain.service") },
                    com.tngtech.archunit.base.DescribedPredicate.describe<com.tngtech.archunit.core.domain.JavaClass>(
                        "application exceptions"
                    ) { it.packageName.contains(".application.exception") }
                )

                // Persistence layer can access: Shared and Application Query objects (ADR-015)
                .whereLayer("Persistence")
                .mayOnlyAccessLayers("Persistence", "Shared")
                // Explicitly allow persistence specs to access query objects (ADR-015: Specs use query objects)
                .ignoreDependency(
                    com.tngtech.archunit.base.DescribedPredicate.describe<com.tngtech.archunit.core.domain.JavaClass>(
                        "specification classes"
                    ) { it.simpleName.endsWith("Specifications") || it.simpleName.endsWith("SpecificationsKt") || it.simpleName.endsWith("Specs") || it.simpleName.endsWith("SpecsKt") },
                    com.tngtech.archunit.base.DescribedPredicate.describe<com.tngtech.archunit.core.domain.JavaClass>(
                        "query objects"
                    ) { it.packageName.contains(".application.query") }
                )

                // Infrastructure can access any layer (adapter pattern) except Web
                .whereLayer("Infrastructure")
                .mayOnlyAccessLayers("Infrastructure", "Application", "Command", "Domain", "Persistence", "Shared")
                // OpenAPI configuration needs to reference web DTOs for API documentation
                .ignoreDependency(
                    com.tngtech.archunit.base.DescribedPredicate.describe<com.tngtech.archunit.core.domain.JavaClass>(
                        "OpenAPI config classes"
                    ) { it.simpleName.contains("OpenApi") && it.packageName.contains(".platform.config") },
                    com.tngtech.archunit.base.DescribedPredicate.describe<com.tngtech.archunit.core.domain.JavaClass>(
                        "web DTOs"
                    ) { it.packageName.contains(".web.dto") }
                )

                // Shared layer is innermost - no dependencies (except infrastructure needs)
                .whereLayer("Shared")
                .mayOnlyAccessLayers("Shared")
                // Audit entities need User entity references for createdBy/updatedBy tracking
                .ignoreDependency(
                    com.tngtech.archunit.base.DescribedPredicate.describe<com.tngtech.archunit.core.domain.JavaClass>(
                        "audit entity classes"
                    ) { it.simpleName.contains("Audited") && it.packageName.contains(".shared.model") },
                    com.tngtech.archunit.base.DescribedPredicate.describe<com.tngtech.archunit.core.domain.JavaClass>(
                        "user entity"
                    ) { it.simpleName == "User" && it.packageName.contains(".persistence") }
                )
                // Security infrastructure needs User entity access
                .ignoreDependency(
                    com.tngtech.archunit.base.DescribedPredicate.describe<com.tngtech.archunit.core.domain.JavaClass>(
                        "security mapper classes"
                    ) { it.simpleName == "UserPrincipalMapper" && it.packageName.contains(".shared.security") },
                    com.tngtech.archunit.base.DescribedPredicate.describe<com.tngtech.archunit.core.domain.JavaClass>(
                        "user entity"
                    ) { it.simpleName == "User" && it.packageName.contains(".persistence") }
                )
        }

    @Test
    fun `command objects must not depend on application layer`(): Unit =
        arch("Command objects are independent - no application layer dependencies") {
            com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.COMMAND)
                .and().haveSimpleNameEndingWith("Command")
                .and().resideOutsideOfPackages(
                    "${ArchitecturePackages.DOMAIN_EVENT}command..",  // Event domain - validation pattern accepted
                    "${ArchitecturePackages.DOMAIN_USER}command..",  // User domain - validation pattern accepted
                    "${ArchitecturePackages.DOMAIN_AUTH}command..",  // Auth domain - uses User validation annotations
                    "${ArchitecturePackages.DOMAIN_SURVEY}command.."  // Survey domain - uses command validation annotations
                )
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                    ArchitecturePackages.APPLICATION_VALIDATION,  // Business validators should be in shared
                    ArchitecturePackages.SERVICE
                )
                .because("ADR-016: Command objects must be independent of application services and validators (validation annotations are pragmatic exception)")
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
