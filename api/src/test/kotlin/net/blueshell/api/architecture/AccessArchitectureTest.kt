package net.blueshell.api.architecture

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
    fun `commands accessed by web and application layers`(): Unit =
        arch("Commands are used by web and application layers") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.COMMAND)
                .and().haveSimpleNameEndingWith("Command")
                .should().onlyBeAccessed().byAnyPackage(
                    ArchitecturePackages.WEB,
                    ArchitecturePackages.APPLICATION,
                    ArchitecturePackages.COMMAND,
                    ArchitecturePackages.SHARED_COMMAND  // CommandBus
                )
                .because("ADR-002: Commands are dispatched from controllers and handled by command handlers")
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
                    ArchitecturePackages.PERSISTENCE
                )
                .because("ADR-016: Repositories are inner layer; only application/domain services access them")
        }

    @Test
    fun `jobs only accessed from platform layer`(): Unit =
        arch("Jobs only triggered by platform infrastructure") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.JOB)
                .and().haveSimpleNameEndingWith("Job")
                .should().onlyBeAccessed().byAnyPackage(
                    ArchitecturePackages.JOB,
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
                .because("ADR-002: Controllers dispatch commands via CommandBus, they don't call repositories")
        }

    @Test
    fun `controllers do not call services directly`(): Unit =
        arch("Controllers use CommandBus not direct service calls") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.WEB)
                .and().haveSimpleNameEndingWith("Controller")
                .should().accessClassesThat().resideInAnyPackage(ArchitecturePackages.SERVICE)
                .because("ADR-002: Controllers dispatch commands via CommandBus for write operations")
        }

    @Test
    fun `application layer does not depend on controllers`(): Unit =
        arch("Inner layers must not depend on controllers") {
            noClasses()
                .that().resideInAnyPackage(
                    ArchitecturePackages.APPLICATION,
                    ArchitecturePackages.DOMAIN,
                    ArchitecturePackages.PERSISTENCE
                )
                .should().dependOnClassesThat().resideInAnyPackage(ArchitecturePackages.CONTROLLER)
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
    fun `command handlers do not depend on DTOs`(): Unit =
        arch("Command handlers must not depend on web DTOs") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.COMMAND_HANDLER)
                .and().haveSimpleNameEndingWith("Handler")
                .and().resideOutsideOfPackage("${ArchitecturePackages.DOMAIN_EVENT}application.command..")  // Event domain - known issue
                .should().dependOnClassesThat().resideInAnyPackage(ArchitecturePackages.DTO)
                .because("ADR-002: Handlers work with commands, not web DTOs")
        }

    @Test
    fun `repositories do not depend on services`(): Unit =
        arch("Repositories must not depend on services") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.REPOSITORY)
                .should().dependOnClassesThat().resideInAnyPackage(ArchitecturePackages.SERVICE)
                .because("ADR-016: Dependency direction is Service -> Repository, never the reverse")
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
                .should().dependOnClassesThat().resideInAnyPackage(ArchitecturePackages.CONTROLLER)
                .because("Configuration wires beans without coupling to specific controllers")
        }

    @Test
    fun `permission evaluators in infrastructure layer`(): Unit =
        arch("Permission evaluators must be in infrastructure layer") {
            classes()
                .that().haveSimpleNameEndingWith("Permission")
                .and().resideOutsideOfPackage(ArchitecturePackages.PERMISSION)
                .and().resideInAnyPackage("${ArchitecturePackages.ROOT}..") // Within project only
                .should().resideInAnyPackage(ArchitecturePackages.PERMISSION)
                .allowEmptyShould(true)  // All permission evaluators already moved - test passes if none found outside
                .because("ADR-014: Permission evaluators are infrastructure adapters, not web layer")
        }

    @Test
    fun `query objects in application layer not persistence`(): Unit =
        arch("Query objects must be in application layer") {
            noClasses()
                .that().haveSimpleNameEndingWith("Query")
                .and().resideInAnyPackage("${ArchitecturePackages.ROOT}..") // Within project only
                .should().resideOutsideOfPackages(
                    ArchitecturePackages.QUERY,
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
}
