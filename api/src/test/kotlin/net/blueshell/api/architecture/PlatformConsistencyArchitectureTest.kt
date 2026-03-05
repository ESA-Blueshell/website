package net.blueshell.api.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import net.blueshell.api.architecture.support.ArchJUnitTestBase
import net.blueshell.api.platform.integration.queue.AbstractJsonJobHandler
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * ArchUnit tests enforcing consistency rules for platform/integration modules.
 *
 * Groups:
 *   A — Job handler structure (A1–A3): enforce AbstractJsonJobHandler inheritance and lifecycle
 *   B — Adapter profile conventions (B1–B3): prevent test/prod contamination
 *   C — Repository/specification naming (C1–C2): enforce standard package paths
 *   D — Platform controller access control (D1): mirror domain controller rules
 *
 * Known violations (expected failures):
 *   C1 — JobExecutionRepository lives at ..job.repository.. instead of ..persistence.repository..
 *   D1 — JobManagementController.getStats() injects JobExecutionRepository directly
 *
 * Aligned with ADR-019 (Anti-Corruption Layers) and ADR-022 (Platform Organization).
 */
class PlatformConsistencyArchitectureTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {

    // ── Group A: Job Handler Structure ────────────────────────────────────────

    /**
     * A1: Concrete job handlers in ..job.. packages must extend AbstractJsonJobHandler.
     *
     * Rationale: enforces transactional wrapping, JSON deserialization, and execution-ID
     * propagation inherited from the base class; prevents bare @Component jobs that bypass
     * all of this. AbstractMailJobHandler itself satisfies this transitively.
     */
    @Test
    fun `job handlers must extend AbstractJsonJobHandler`(): Unit =
        arch("Concrete *Job classes must extend AbstractJsonJobHandler") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.JOB)
                .and().haveSimpleNameEndingWith("Job")
                .and().areNotAbstract()
                .should().beAssignableTo(AbstractJsonJobHandler::class.java)
                .because("ADR-022: Job handlers must extend AbstractJsonJobHandler for transactional wrapping and JSON deserialization")
        }

    /**
     * A2: Concrete job handlers in ..job.. packages must be annotated with @Component.
     *
     * Rationale: AbstractJsonJobHandler subclasses need Spring-managed lifecycle.
     * Mirrors the existing "command handlers are Spring components" rule for the job tier.
     */
    @Test
    fun `job handlers must be annotated with @Component`(): Unit =
        arch("Concrete *Job classes must be @Component") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.JOB)
                .and().haveSimpleNameEndingWith("Job")
                .and().areNotAbstract()
                .should().beAnnotatedWith(Component::class.java)
                .because("ADR-022: Job handlers need Spring-managed lifecycle (@Component)")
        }

    /**
     * A3: handlePayload methods on AbstractJsonJobHandler subclasses must not be @Transactional.
     *
     * Rationale: AbstractJsonJobHandler.handle() already applies @Transactional.
     * Adding it again on handlePayload creates nested-transaction surprises.
     */
    @Test
    fun `job handler handlePayload must not be @Transactional`(): Unit =
        arch("handlePayload methods in job handlers must not be @Transactional") {
            methods()
                .that().haveName("handlePayload")
                .and().areDeclaredInClassesThat().resideInAnyPackage(ArchitecturePackages.JOB)
                .and().areDeclaredInClassesThat()
                    .areAssignableTo(AbstractJsonJobHandler::class.java)
                .should().notBeAnnotatedWith(Transactional::class.java)
                .because("AbstractJsonJobHandler.handle() is already @Transactional; annotating handlePayload creates nested-transaction surprises")
        }

    // ── Group B: Adapter Profile Conventions ─────────────────────────────────

    /**
     * B1: Production adapters (ending with 'Adapter') must declare @Profile.
     *
     * Rationale: without @Profile, a production adapter (real external API) activates in test
     * environments, causing integration test pollution.
     *
     * Note: only targets *Adapter (not *Client) because clients are lower-level infrastructure
     * wired via @Bean methods on @Configuration classes that themselves carry @Profile.
     */
    @Test
    fun `production adapters must declare @Profile`(): Unit =
        arch("Production *Adapter classes must be annotated with @Profile") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.PLATFORM_INTEGRATION)
                .and().haveSimpleNameEndingWith("Adapter")
                .and().areNotAbstract()
                .and().resideOutsideOfPackage(ArchitecturePackages.PLATFORM_MOCK)
                .should(beAnnotatedWithProfile())
                .because("ADR-022: Production adapters must have @Profile to avoid test environment pollution")
        }

    /**
     * B2: Mock adapters in platform.integration.mock must be annotated with @Primary.
     *
     * Rationale: without @Primary, Spring throws NoUniqueBeanDefinitionException at test startup
     * when both real and mock implementations are on the classpath.
     */
    @Test
    fun `mock adapters must be @Primary`(): Unit =
        arch("Classes in platform.integration.mock must be @Primary") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.PLATFORM_MOCK)
                .and().areNotAbstract()
                .should().beAnnotatedWith(Primary::class.java)
                .because("ADR-022: Mock adapters need @Primary to override production beans in test/dev profiles")
        }

    /**
     * B3: Mock adapters must target test or dev profiles.
     *
     * Rationale: a mock without a test-scoped profile would silently discard real calls in production.
     */
    @Test
    fun `mock adapters must target test or dev profiles`(): Unit =
        arch("Classes in platform.integration.mock must have @Profile containing 'test' or 'dev'") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.PLATFORM_MOCK)
                .and().areNotAbstract()
                .should(haveTestOrDevProfile())
                .because("ADR-022: Mock adapters must be scoped to test/dev profiles to prevent production activation")
        }

    // ── Group C: Repository / Specification Naming Consistency ───────────────

    /**
     * C1: Platform repositories must reside in ..persistence.repository.. packages.
     *
     * EXPECTED FAILURE: JobExecutionRepository lives at ..job.repository.. (pre-convergence tech debt).
     * Fix: move to ..job.persistence.repository..
     *
     * Rationale: standard layout makes repositories discoverable and ensures the existing
     * "repository only accessed by application/persistence layers" rule applies uniformly.
     */
    @Test
    fun `platform repositories must reside in persistence dot repository packages`(): Unit =
        arch("Platform *Repository interfaces must be in ..persistence.repository.. packages") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.PLATFORM_INTEGRATION)
                .and().haveSimpleNameEndingWith("Repository")
                .should().resideInAnyPackage("${ArchitecturePackages.ROOT}.platform.integration..persistence.repository..")
                .because("ADR-022: Standard layout requires repositories at ..persistence.repository.. for rule uniformity")
        }

    /**
     * C2: Platform specifications must reside in ..persistence.spec.. packages.
     *
     * Rationale: mirrors the existing domain pattern and ensures the SPECIFICATION
     * constant applies consistently to all specs.
     */
    @Test
    fun `platform specifications must reside in persistence dot spec packages`(): Unit =
        arch("Platform *Specifications classes must be in ..persistence.spec.. packages") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.PLATFORM_INTEGRATION)
                .and().haveSimpleNameEndingWith("Specifications")
                .and().areNotAbstract()
                .should().resideInAnyPackage("${ArchitecturePackages.ROOT}.platform.integration..persistence.spec..")
                .allowEmptyShould(true)
                .because("ADR-022: Standard layout requires specifications at ..persistence.spec..")
        }

    // ── Group D: Platform Controller Repository Access Control ───────────────

    /**
     * D1: Platform controllers must not access any platform repository directly.
     *
     * EXPECTED FAILURE: JobManagementController.getStats() directly injects JobExecutionRepository
     * to call countByStatus(). Should be moved to JobExecutionService.
     *
     * Rationale: mirrors the existing "controllers do not access repositories directly" rule;
     * the job module's ..job.repository.. path falls outside REPOSITORY = "..persistence.repository.."
     * so the original rule does not catch this violation.
     */
    @Test
    fun `platform controllers must not access any platform repository directly`(): Unit =
        arch("Platform *Controller classes must not access any platform repository") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.PLATFORM_INTEGRATION)
                .and().haveSimpleNameEndingWith("Controller")
                .should().accessClassesThat()
                    .resideInAnyPackage(ArchitecturePackages.PLATFORM_ANY_REPOSITORY)
                .because("ADR-002/ADR-022: Controllers must not access repositories directly; use service layer instead")
        }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun beAnnotatedWithProfile(): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("be annotated with @Profile") {
            override fun check(clazz: JavaClass, events: ConditionEvents) {
                val hasProfile = clazz.isAnnotatedWith(Profile::class.java)
                if (!hasProfile) {
                    events.add(
                        SimpleConditionEvent.violated(
                            clazz,
                            "${clazz.name} is missing @Profile — production adapters must declare a profile to prevent test environment pollution"
                        )
                    )
                }
            }
        }

    private fun haveTestOrDevProfile(): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("have @Profile value containing 'test' or 'dev'") {
            override fun check(clazz: JavaClass, events: ConditionEvents) {
                val profileAnnotation = clazz.tryGetAnnotationOfType(Profile::class.java)
                if (!profileAnnotation.isPresent) {
                    events.add(
                        SimpleConditionEvent.violated(
                            clazz,
                            "${clazz.name} is missing @Profile — mock adapters must have @Profile('test') or @Profile('test | dev')"
                        )
                    )
                    return
                }
                val value = profileAnnotation.get().value.joinToString("|").lowercase()
                if (!value.contains("test") && !value.contains("dev")) {
                    events.add(
                        SimpleConditionEvent.violated(
                            clazz,
                            "${clazz.name} has @Profile($value) which does not contain 'test' or 'dev' — mock adapters must target test/dev profiles"
                        )
                    )
                }
            }
        }
}
