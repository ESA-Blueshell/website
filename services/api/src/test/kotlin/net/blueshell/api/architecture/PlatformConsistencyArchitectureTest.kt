package net.blueshell.api.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaModifier
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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * ArchUnit tests enforcing consistency rules for platform/integration modules.
 *
 * Groups:
 *   A — Job handler structure (A1–A3): enforce AbstractJsonJobHandler inheritance and lifecycle
 *   B — Adapter profile conventions (B1–B3): prevent test/prod contamination
 *   C — Repository/specification naming (C1–C2): enforce standard package paths
 *   D — Platform controller access control (D1): mirror domain controller rules
 *   E — Adapter/Client placement (E1–E2): adapters and clients must reside in ..adapter..
 *   F — Service placement (F1): @Service beans outside adapter/mock/queue must be in ..application..
 *   G — DTO placement (G1): DTOs must reside in ..web.dto..
 *   H — Scheduler placement (H1): schedulers must reside in ..application..
 *   I — Job handler placement (I1): concrete *Job classes must reside in ..application.job..
 *   J — Queue isolation (J1): queue classes must not access platform repositories directly
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
                .and().doNotHaveModifier(JavaModifier.ABSTRACT)
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
                .and().doNotHaveModifier(JavaModifier.ABSTRACT)
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
                .and().doNotHaveModifier(JavaModifier.ABSTRACT)
                .and().resideOutsideOfPackages(ArchitecturePackages.PLATFORM_MOCK)
                .should(beAnnotatedWithProfile())
                .because("ADR-022: Production adapters must have @Profile to avoid test environment pollution")
        }

    /**
     * B2: Mock adapters in platform.integration.mock must be annotated with @Primary.
     *
     * Rationale: without @Primary, Spring throws NoUniqueBeanDefinitionException at test startup
     * when both real and mock implementations are on the classpath.
     *
     * Only targets Spring bean classes (@Service/@Component) — excludes helper data classes,
     * companion objects, and nested data classes defined alongside the mock adapters.
     */
    @Test
    fun `mock adapters must be @Primary`(): Unit =
        arch("Spring bean classes in platform.integration.mock must be @Primary") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.PLATFORM_MOCK)
                .and(isSpringBean())
                .should().beAnnotatedWith(Primary::class.java)
                .because("ADR-022: Mock adapters need @Primary to override production beans in test/dev profiles")
        }

    /**
     * B3: Mock adapters must target test or dev profiles.
     *
     * Rationale: a mock without a test-scoped profile would silently discard real calls in production.
     *
     * Only targets Spring bean classes (@Service/@Component) — see B2.
     */
    @Test
    fun `mock adapters must target test or dev profiles`(): Unit =
        arch("Spring bean classes in platform.integration.mock must have @Profile containing 'test' or 'dev'") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.PLATFORM_MOCK)
                .and(isSpringBean())
                .should(haveTestOrDevProfile())
                .because("ADR-022: Mock adapters must be scoped to test/dev profiles to prevent production activation")
        }

    // ── Group C: Repository / Specification Naming Consistency ───────────────

    /**
     * C1: Platform repositories must reside in ..persistence.repository.. packages.
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
                .and().doNotHaveModifier(JavaModifier.ABSTRACT)
                .should().resideInAnyPackage("${ArchitecturePackages.ROOT}.platform.integration..persistence.spec..")
                .allowEmptyShould(true)
                .because("ADR-022: Standard layout requires specifications at ..persistence.spec..")
        }

    // ── Group D: Platform Controller Repository Access Control ───────────────

    /**
     * D1: Platform controllers must not access any platform repository directly.
     *
     * Rationale: mirrors the existing "controllers do not access repositories directly" rule;
     * controllers must use the service layer instead of accessing repositories directly.
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

    // ── Group E: Adapter/Client Placement ────────────────────────────────────

    /**
     * E1: Production *Adapter classes (outside mock) must reside in ..adapter.. packages.
     *
     * Rationale: ACL adapters must be in the adapter sub-package for the standard layout;
     * placing them at the module root mixes infrastructure concerns with application logic.
     */
    @Test
    fun `production adapters must reside in adapter packages`(): Unit =
        arch("Production *Adapter classes must reside in ..adapter.. packages") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.PLATFORM_INTEGRATION)
                .and().haveSimpleNameEndingWith("Adapter")
                .and().doNotHaveModifier(JavaModifier.ABSTRACT)
                .and().resideOutsideOfPackages(ArchitecturePackages.PLATFORM_MOCK)
                .should().resideInAnyPackage(ArchitecturePackages.PLATFORM_ADAPTER)
                .because("ADR-022: Production adapters must reside in ..adapter.. sub-package")
        }

    /**
     * E2: *Client classes in PLATFORM_INTEGRATION (outside mock) must reside in ..adapter.. packages.
     *
     * Rationale: low-level HTTP/API clients are adapter-layer infrastructure and must be co-located
     * with their adapter counterparts, not scattered at the module root.
     */
    @Test
    fun `platform clients must reside in adapter packages`(): Unit =
        arch("*Client classes in platform.integration must reside in ..adapter.. packages") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.PLATFORM_INTEGRATION)
                .and().haveSimpleNameEndingWith("Client")
                .and().resideOutsideOfPackages(ArchitecturePackages.PLATFORM_MOCK)
                .should().resideInAnyPackage(ArchitecturePackages.PLATFORM_ADAPTER)
                .because("ADR-022: Platform clients must reside in ..adapter.. sub-package alongside their adapters")
        }

    // ── Group F: Service Placement ────────────────────────────────────────────

    /**
     * F1: @Service beans in PLATFORM_INTEGRATION (outside adapter/mock/queue) must reside in ..application..
     *
     * Rationale: application services encapsulate business orchestration and must be in the
     * application sub-package; placing them at the module root or in service/ at the root level
     * bypasses the standard layer structure.
     */
    @Test
    fun `platform services must reside in application packages`(): Unit =
        arch("@Service classes in platform.integration must reside in ..application.. packages") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.PLATFORM_INTEGRATION)
                .and().areAnnotatedWith(Service::class.java)
                .and().resideOutsideOfPackages(
                    ArchitecturePackages.PLATFORM_ADAPTER,
                    ArchitecturePackages.PLATFORM_MOCK,
                    ArchitecturePackages.PLATFORM_QUEUE,
                )
                .should().resideInAnyPackage(ArchitecturePackages.PLATFORM_APPLICATION)
                .because("ADR-022: Platform @Service beans must reside in ..application.. sub-package")
        }

    // ── Group G: DTO Placement ────────────────────────────────────────────────

    /**
     * G1: *DTO classes in PLATFORM_INTEGRATION must reside in ..web.dto.. packages.
     *
     * Rationale: DTOs are web-layer presentation objects; placing them in a generic dto/ at
     * the module root conflates the web boundary with internal packages.
     */
    @Test
    fun `platform DTOs must reside in web dto packages`(): Unit =
        arch("*DTO classes in platform.integration must reside in ..web.dto.. packages") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.PLATFORM_INTEGRATION)
                .and().haveSimpleNameEndingWith("DTO")
                .should().resideInAnyPackage(ArchitecturePackages.PLATFORM_WEB_DTO)
                .because("ADR-022: Platform DTOs are web-layer objects and must reside in ..web.dto..")
        }

    // ── Group H: Scheduler Placement ─────────────────────────────────────────

    /**
     * H1: Spring bean classes named *Scheduler in PLATFORM_INTEGRATION must reside in ..application..
     *
     * Rationale: schedulers coordinate application-level background tasks and belong in the
     * application sub-package alongside services, not at the module root.
     */
    @Test
    fun `platform schedulers must reside in application packages`(): Unit =
        arch("*Scheduler Spring bean classes in platform.integration must reside in ..application.. packages") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.PLATFORM_INTEGRATION)
                .and().haveSimpleNameEndingWith("Scheduler")
                .and(isSpringBean())
                .should().resideInAnyPackage(ArchitecturePackages.PLATFORM_APPLICATION)
                .because("ADR-022: Platform schedulers must reside in ..application.. sub-package")
        }

    // ── Group I: Job Handler Placement ───────────────────────────────────────

    /**
     * I1: Concrete *Job classes in PLATFORM_INTEGRATION must reside in ..application.job.. packages.
     *
     * Rationale: job handlers are application-layer components; placing them in a flat job/
     * directory at the module root bypasses the standard 3-layer integration structure.
     * Groups A1–A3 enforce inheritance and lifecycle; I1 enforces placement.
     */
    /**
     * I1: Concrete job handlers must reside in either the legacy
     * `..application.job..` location or the hexagonal `..adapter.job..`
     * location.
     *
     * A job handler is a driving (inbound) adapter — it adapts the queue's
     * "execute this payload" message into a call against an inbound
     * application port. In a true hexagonal split it lives under
     * `adapter/job/`. The legacy placement under `application/job/` is
     * accepted while the rest of the codebase migrates; new modules
     * should land directly under `adapter/job/`.
     */
    @Test
    fun `platform job handlers must reside in a job sub-package`(): Unit =
        arch("Concrete *Job classes in platform.integration must reside in ..application.job.. or ..adapter.job.. packages") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.PLATFORM_INTEGRATION)
                .and().haveSimpleNameEndingWith("Job")
                .and().doNotHaveModifier(JavaModifier.ABSTRACT)
                .should().resideInAnyPackage(
                    ArchitecturePackages.APPLICATION_JOB,
                    ArchitecturePackages.ADAPTER_JOB,
                )
                .because("ADR-022: Job handlers are driving adapters and belong in a job sub-package")
        }

    // ── Group J: Queue Isolation ──────────────────────────────────────────────

    /**
     * J1: Classes in platform.integration.queue must not directly access platform repositories.
     *
     * Rationale: the queue infrastructure layer must only coordinate execution via the service
     * layer; direct repository access in queue classes bypasses transactional service logic
     * and creates unwanted coupling between queue infrastructure and persistence.
     */
    @Test
    fun `queue classes must not access platform repositories directly`(): Unit =
        arch("Classes in platform.integration.queue must not access platform repositories") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.PLATFORM_QUEUE)
                .should().accessClassesThat()
                    .resideInAnyPackage(ArchitecturePackages.PLATFORM_ANY_REPOSITORY)
                .because("ADR-022: Queue infrastructure must not access repositories directly; use the service layer instead")
        }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Matches classes that are Spring-managed beans (@Component or @Service). */
    private fun isSpringBean(): DescribedPredicate<JavaClass> =
        DescribedPredicate.describe("is a Spring bean (@Component or @Service)") { clazz ->
            clazz.isAnnotatedWith(Component::class.java) || clazz.isAnnotatedWith(Service::class.java)
        }

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
