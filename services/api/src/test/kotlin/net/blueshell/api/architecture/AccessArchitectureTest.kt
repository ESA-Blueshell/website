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
 *
 * `only the web layer reaches a domain module's web package` went the same way. It selected
 * `net.blueshell.api.domain..web..`, a grouping level the flattening removed, and
 * [CrossModuleWebAccessArchitectureTest] now states the stronger rule against today's packages:
 * no module reaches another module's web package at all, whatever layer the reach comes from.
 *
 * Three more went with the stale constants they read (#1159):
 *
 * `dto only accessed at api boundary` selected `..web.dto..`, a folder the flattening emptied —
 * input and response types sit directly in `<module>/web` now, so there is no DTO package left to
 * ring-fence. What it was defending is stated against today's packages by
 * `application services do not depend on DTOs` inside a module, and by
 * [CrossModuleWebAccessArchitectureTest] across modules.
 *
 * `controllers do not access repositories directly` named `..persistence.repository..` on its
 * `should` side, a folder the flattening merged into `<module>/persistence`.
 * [DataOwnershipArchitectureTest]'s `web validators should use services not repositories` asks the
 * wider question against today's packages — every class in a `web` package rather than the
 * controllers alone, and `dependOnClassesThat`, which covers field, parameter and return types as
 * well as the calls `accessClassesThat` sees.
 *
 * `persistence must not depend on application layer` carved a module's `application` package into
 * query objects, which persistence could use under ADR-015, and everything else, which it could
 * not. The flattening put both in `<module>/domain`, so the line it drew is no longer a line any
 * package boundary can express. `repositories do not depend on services` keeps the half that can
 * still be named, and now covers all of persistence rather than repositories alone.
 */
class AccessArchitectureTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {

    @Test
    fun `repository only accessed by application and persistence layers`(): Unit =
        arch("Repositories only accessed from application layer") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.MODULE_PERSISTENCE)
                .and().haveSimpleNameEndingWith("Repository")
                .should().onlyBeAccessed().byAnyPackage(
                    *ArchitecturePackages.SERVICE_LAYER,   // job handlers included: they live here now
                    ArchitecturePackages.MODULE_PERSISTENCE,
                    ArchitecturePackages.PLATFORM_MOCK     // Mock job handlers in test/dev profile
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
                    ArchitecturePackages.MODULE_DOMAIN,  // event listeners live here too
                    ArchitecturePackages.MODULE_API,
                    ArchitecturePackages.PLATFORM
                )
                .because("Jobs should be triggered by event listeners or scheduling infrastructure")
        }

    @Test
    fun `application layer does not depend on controllers`(): Unit =
        arch("Inner layers must not depend on controllers") {
            noClasses()
                .that().resideInAnyPackage(
                    *ArchitecturePackages.SERVICE_LAYER,
                    ArchitecturePackages.MODULE_PERSISTENCE
                )
                .should().dependOnClassesThat(webControllers)
                .because("ADR-016: Inner layers must not depend on web layer")
        }

    @Test
    fun `application services do not depend on DTOs`(): Unit =
        arch("Application services must not depend on web DTOs") {
            noClasses()
                .that().resideInAnyPackage(*ArchitecturePackages.SERVICE_LAYER)
                .and().haveSimpleNameEndingWith("Service")
                .should().dependOnClassesThat().resideInAnyPackage(ArchitecturePackages.MODULE_WEB)
                .because("ADR-001: Keep DTO usage at boundary; application works with entities and commands")
        }

    @Test
    fun `repositories do not depend on services`(): Unit =
        arch("Persistence must not depend on services") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.MODULE_PERSISTENCE)
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
    fun `repositories do not depend on DTOs`(): Unit =
        arch("Repositories must not depend on DTOs") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.MODULE_PERSISTENCE)
                .and().haveSimpleNameEndingWith("Repository")
                .should().dependOnClassesThat().resideInAnyPackage(ArchitecturePackages.MODULE_WEB)
                .because("ADR-016: Persistence layer should not know about web DTOs")
        }

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
                    ArchitecturePackages.MODULE_DOMAIN,  // where the flattening puts them
                    ArchitecturePackages.WEB  // Acceptable for web query params
                )
                .because("ADR-015: Query objects are application concerns, not persistence filters")
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
            JavaClass.Predicates.resideInAnyPackage(*ArchitecturePackages.SERVICE_LAYER)
                .and(JavaClass.Predicates.simpleNameEndingWith("Service"))
                .`as`("application services")
    }
}
