package net.blueshell.api.architecture

import com.tngtech.archunit.core.domain.JavaClass
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
 * 2. Application Layer (Use cases, Services, Business Validators)
 * 3. Domain Layer (optional - Rich domain models)
 * 4. Persistence Layer (Entities, Repositories)
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

                // Web layer can access: Application, Domain, Persistence, Shared, Infrastructure
                .whereLayer("Web")
                .mayOnlyAccessLayers("Web", "Application", "Domain", "Persistence", "Infrastructure", "Shared")

                // Application layer can access: Domain, Persistence, Shared, Infrastructure
                .whereLayer("Application")
                .mayOnlyAccessLayers("Application", "Domain", "Persistence", "Infrastructure", "Shared")

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
                .mayOnlyAccessLayers("Infrastructure", "Application", "Domain", "Persistence", "Shared")
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
                // Job payloads in `shared/job/` are queue-transport DTOs that carry the inputs
                // of an inbound use-case port. In a hexagonal split the verb that names the
                // operation (intent enums, request types) belongs to the application's inbound
                // port, and the transport DTO references it — driving-adapter direction. We
                // accept that direction by exempting shared/job payload types from referencing
                // platform.integration..port.in.. types. Same direction, just expressed through
                // the queue transport instead of a direct method call.
                .ignoreDependency(
                    com.tngtech.archunit.base.DescribedPredicate.describe<com.tngtech.archunit.core.domain.JavaClass>(
                        "queue job payload definitions"
                    ) { it.packageName.contains(".shared.job") },
                    com.tngtech.archunit.base.DescribedPredicate.describe<com.tngtech.archunit.core.domain.JavaClass>(
                        "inbound application ports"
                    ) { it.packageName.contains(".platform.integration") && it.packageName.contains(".port.in") }
                )
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
                .should().dependOnClassesThat(
                    JavaClass.Predicates.resideInAnyPackage(
                        ArchitecturePackages.APPLICATION_VALIDATION,
                        ArchitecturePackages.LISTENER,
                        ArchitecturePackages.EVENT,
                        ArchitecturePackages.FACTORY
                    ).or(
                        // Services are named, not packaged: a `*Service` glob matches no package.
                        JavaClass.Predicates.resideInAnyPackage(ArchitecturePackages.APPLICATION)
                            .and(JavaClass.Predicates.simpleNameEndingWith("Service"))
                    ).`as`("application services, validators, listeners, events or factories")
                )
                // Note: ArchitecturePackages.QUERY is intentionally omitted (ADR-015: Specs can use query objects)
                .because("ADR-016: Persistence can depend on query objects (ADR-015), but not services/handlers/validators")
        }
}
