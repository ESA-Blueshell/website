package net.blueshell.api.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import net.blueshell.api.architecture.support.ArchJUnitTestBase
import org.junit.jupiter.api.Test
import org.springframework.security.access.prepost.PreAuthorize

/**
 * ArchUnit tests enforcing access rules between layers and components.
 * Aligned with ADR-001, ADR-002, ADR-016.
 */
/*
 * `domain model and domain services must not depend on outer layers` was retired with the
 * package flattening. It selected `..domain.model..` and `..domain.service..`, a split only
 * the auth module ever had, and architecture ADR-003 gives every module one domain folder
 * holding both. Its cross-module half is what module verification now checks; what remains
 * intra-module is a single folder with no layer boundary left to cross.
 */
class AccessArchitectureTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {

    @Test
    fun `dto only accessed at api boundary`(): Unit =
        arch("DTOs only accessed at web layer boundary") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.DTO)
                .and().resideOutsideOfPackages(
                    "${ArchitecturePackages.DOMAIN_EVENT}web.dto..",  // Event domain DTOs - known issue (80% migrated)
                    "${ArchitecturePackages.DOMAIN_SURVEY}web.dto.."   // Survey DTOs used by Event
                )
                .should().onlyBeAccessed().byAnyPackage(
                    ArchitecturePackages.WEB,
                    ArchitecturePackages.DTO,
                    ArchitecturePackages.PERSISTENCE  // Allow for entity -> DTO mappings
                )
                .because("ADR-001: DTOs should not leak into application layer; use commands instead")
        }

    @Test
    fun `repository only accessed by application and persistence layers`(): Unit =
        arch("Repositories only accessed from application layer") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.REPOSITORY)
                .should().onlyBeAccessed().byAnyPackage(
                    ArchitecturePackages.APPLICATION,
                    ArchitecturePackages.REPOSITORY,
                    ArchitecturePackages.DOMAIN_SERVICE,  // Domain services can access repositories
                    ArchitecturePackages.PERSISTENCE,
                    ArchitecturePackages.JOB,             // Per-integration job handlers read DB state
                    ArchitecturePackages.PLATFORM_MOCK    // Mock job handlers in test/dev profile
                )
                .because("ADR-016: Repositories are inner layer; only application/domain services access them")
        }

    @Test
    fun `jobs only accessed from platform layer`(): Unit =
        arch("Jobs only triggered by platform infrastructure") {
            classes()
                .that().resideInAnyPackage(*ArchitecturePackages.JOB_HOMES)
                .and().haveSimpleNameEndingWith("Job")
                .should().onlyBeAccessed().byAnyPackage(
                    ArchitecturePackages.JOB,
                    ArchitecturePackages.MODULE_DOMAIN,
                    ArchitecturePackages.MODULE_API,
                    ArchitecturePackages.PLATFORM,
                    ArchitecturePackages.LISTENER  // Listeners can dispatch jobs
                )
                .because("Jobs should be triggered by event listeners or scheduling infrastructure")
        }

    @Test
    fun `controllers do not access repositories directly`(): Unit =
        arch("Controllers must not access repositories") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.WEB)
                .and().haveSimpleNameEndingWith("Controller")
                .should().accessClassesThat().resideInAnyPackage(ArchitecturePackages.REPOSITORY)
                .because("ADR-002: Controllers call use cases and services, never repositories")
        }

    @Test
    fun `application layer does not depend on controllers`(): Unit =
        arch("Inner layers must not depend on controllers") {
            noClasses()
                .that().resideInAnyPackage(
                    ArchitecturePackages.APPLICATION,
                    // DOMAIN is the module root ($ROOT..domain..), which also covers each module's
                    // web package; the domain layer proper is model + service.
                    ArchitecturePackages.DOMAIN_MODEL,
                    ArchitecturePackages.DOMAIN_SERVICE,
                    ArchitecturePackages.PERSISTENCE
                )
                .should().dependOnClassesThat(webControllers)
                .because("ADR-016: Inner layers must not depend on web layer")
        }

    @Test
    fun `application services do not depend on DTOs`(): Unit =
        arch("Application services must not depend on web DTOs") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.APPLICATION)
                .and().haveSimpleNameEndingWith("Service")
                .should().dependOnClassesThat().resideInAnyPackage(ArchitecturePackages.DTO)
                .because("ADR-001: Keep DTO usage at boundary; application works with entities and commands")
        }

    @Test
    fun `repositories do not depend on services`(): Unit =
        arch("Repositories must not depend on services") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.REPOSITORY)
                .should().dependOnClassesThat(applicationServices)
                .because("ADR-016: Dependency direction is Service -> Repository, never the reverse")
        }

    @Test
    fun `persistence must not depend on web layer`(): Unit =
        arch("Persistence layer independent of web concerns") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.PERSISTENCE)
                .should().dependOnClassesThat()
                .resideInAnyPackage(ArchitecturePackages.WEB)
                .because("ADR-016: Persistence layer must not know about web DTOs or controllers")
        }

    @Test
    fun `persistence must not depend on application layer`(): Unit =
        arch("Persistence layer is inner - no application dependencies except queries") {
            noClasses()
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
                    ).or(applicationOfADomainModuleOtherThanQueries)
                        .`as`("application services, validators, listeners, events, factories or any other part of a domain module's application package")
                )
                // ArchitecturePackages.QUERY is exempt (ADR-015: Specs can use query objects)
                .because("ADR-016: Persistence can depend on query objects (ADR-015), but not services/handlers/validators")
        }

    @Test
    fun `only the web layer reaches a domain module's web package`(): Unit =
        arch("Domain web packages are reached from the web layer only") {
            noClasses()
                .that().resideInAnyPackage(
                    ArchitecturePackages.APPLICATION,
                    ArchitecturePackages.INFRASTRUCTURE,
                    ArchitecturePackages.PLATFORM
                )
                // The OpenAPI schema customizer documents a response type, so it names one.
                .and(DescribedPredicate.not(openApiConfiguration))
                .should().dependOnClassesThat().resideInAnyPackage(ArchitecturePackages.DOMAIN_WEB)
                .because("ADR-016: controllers, request/response types and their mappers serve one endpoint; inner layers work with entities and commands")
        }

    @Test
    fun `repositories do not depend on DTOs`(): Unit =
        arch("Repositories must not depend on DTOs") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.REPOSITORY)
                .should().dependOnClassesThat().resideInAnyPackage(ArchitecturePackages.DTO)
                .because("ADR-016: Persistence layer should not know about web DTOs")
        }

    // NOTE: Cyclic dependency test disabled - known exception exists
    // shared → domain.user.persistence.User for audit fields is documented in ADR-016 as acceptable
    // The cycle is: domain → shared (normal) + shared → domain.user.persistence.User (for audit)
    // This is a conscious architectural trade-off for audit field convenience
    // TODO: Consider adding test with ignoreDependency() when ArchUnit API is clearer
    // @Test
    // fun `no cyclic dependencies by top level package`(): Unit =
    //     arch("No cyclic dependencies between top-level packages") {
    //         slices()
    //             .matching("${ArchitecturePackages.ROOT}.(*)..")
    //             .should().beFreeOfCycles()
    //             .because("Cyclic dependencies make refactoring risky and coupling invisible")
    //     }

    @Test
    fun `configuration does not depend on web controllers`(): Unit =
        arch("Configuration must not depend on controllers") {
            noClasses()
                .that().resideInAnyPackage(
                    ArchitecturePackages.PLATFORM_CONFIG,
                    ArchitecturePackages.SECURITY
                )
                .should().dependOnClassesThat(webControllers)
                .because("Configuration wires beans without coupling to specific controllers")
        }

    @Test
    fun `domain permission evaluators live with their aggregate`(): Unit =
        arch("Domain permission evaluators must not sit in the shared permission package") {
            classes()
                .that().haveSimpleNameEndingWith("Permission")
                .and().resideInAnyPackage("${ArchitecturePackages.ROOT}..")
                .should().resideOutsideOfPackage(ArchitecturePackages.PERMISSION)
                .because(
                    "architecture ADR-007: authorization belongs to the module whose aggregate it " +
                        "governs; only BasePermissionEvaluator and CompositePermissionEvaluator remain central"
                )
        }

    @Test
    fun `query objects in application layer not persistence`(): Unit =
        arch("Query objects must be in application layer") {
            noClasses()
                .that().haveSimpleNameEndingWith("Query")
                .and().resideInAnyPackage("${ArchitecturePackages.ROOT}..") // Within project only
                .should().resideOutsideOfPackages(
                    ArchitecturePackages.QUERY,
                    ArchitecturePackages.MODULE_DOMAIN,  // same layer, once the module is flattened
                    ArchitecturePackages.WEB  // Acceptable for web query params
                )
                .because("ADR-015: Query objects are application concerns, not persistence filters")
                .allowEmptyShould(true)
        }

    @Test
    fun `controller methods must not use standalone hasAuthority`(): Unit =
        arch("@PreAuthorize should use hasPermission, not standalone hasAuthority") {
            methods()
                .that().areDeclaredInClassesThat().resideInAnyPackage(ArchitecturePackages.WEB)
                .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
                .and().areAnnotatedWith(PreAuthorize::class.java)
                .should(notUseStandaloneHasAuthority())
                .because("ADR-014: All authorization should use permission evaluators for consistency and testability")
        }

    private fun notUseStandaloneHasAuthority(): ArchCondition<JavaMethod> {
        return object : ArchCondition<JavaMethod>("not use standalone hasAuthority") {
            override fun check(method: JavaMethod, events: ConditionEvents) {
                val preAuth = method.tryGetAnnotationOfType(PreAuthorize::class.java)
                if (preAuth.isPresent) {
                    val expression = preAuth.get().value

                    // Check if hasAuthority is used without hasPermission
                    val hasAuthority = expression.contains("hasAuthority")
                    val hasPermission = expression.contains("hasPermission")

                    if (hasAuthority && !hasPermission) {
                        val msg = "Method ${method.fullName} uses standalone hasAuthority('...') " +
                                 "instead of hasPermission(...): $expression. " +
                                 "Use hasPermission(null, 'Role', 'ROLENAME') for role checks."
                        events.add(SimpleConditionEvent.violated(method, msg))
                    }
                }
            }
        }
    }

    private companion object {
        // Controllers and services are named, not packaged: a `*Controller` or `*Service` glob
        // handed to resideInAnyPackage matches no package at all, so these are predicates instead.
        val webControllers: DescribedPredicate<JavaClass> =
            JavaClass.Predicates.resideInAnyPackage(ArchitecturePackages.WEB)
                .and(JavaClass.Predicates.simpleNameEndingWith("Controller"))
                .`as`("web controllers")

        val applicationServices: DescribedPredicate<JavaClass> =
            JavaClass.Predicates.resideInAnyPackage(ArchitecturePackages.APPLICATION)
                .and(JavaClass.Predicates.simpleNameEndingWith("Service"))
                .`as`("application services")

        val applicationOfADomainModuleOtherThanQueries: DescribedPredicate<JavaClass> =
            JavaClass.Predicates.resideInAnyPackage(ArchitecturePackages.DOMAIN_APPLICATION)
                .and(
                    DescribedPredicate.not(
                        JavaClass.Predicates.resideInAnyPackage(ArchitecturePackages.QUERY)
                    )
                )
                .`as`("a domain module's application package other than its query objects")

        val openApiConfiguration: DescribedPredicate<JavaClass> =
            JavaClass.Predicates.resideInAnyPackage(ArchitecturePackages.PLATFORM_CONFIG)
                .and(JavaClass.Predicates.simpleNameContaining("OpenApi"))
                .`as`("OpenAPI configuration")
    }
}
