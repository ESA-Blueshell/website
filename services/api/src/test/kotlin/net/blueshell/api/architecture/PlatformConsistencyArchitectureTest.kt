package net.blueshell.api.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import net.blueshell.api.architecture.support.ArchJUnitTestBase
import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * ArchUnit consistency rules for the platform modules, aligned with ADR-019 and ADR-022. Each
 * rule states itself; the letters group them — A job handler structure, B adapter profiles,
 * C repository and specification naming, D controller access, E adapter and client placement,
 * F service placement, G DTO placement, H schedulers, I job handlers, J queue isolation.
 */
class PlatformConsistencyArchitectureTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {
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
                .that()
                .resideInAnyPackage(*ArchitecturePackages.JOB_HOMES)
                .and()
                .haveSimpleNameEndingWith("Job")
                .and()
                .doNotHaveModifier(JavaModifier.ABSTRACT)
                .should()
                .beAssignableTo(AbstractJsonJobHandler::class.java)
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
                .that()
                .resideInAnyPackage(*ArchitecturePackages.JOB_HOMES)
                .and()
                .haveSimpleNameEndingWith("Job")
                .and()
                .doNotHaveModifier(JavaModifier.ABSTRACT)
                .should()
                .beAnnotatedWith(Component::class.java)
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
                .that()
                .haveName("handlePayload")
                .and()
                .areDeclaredInClassesThat()
                .resideInAnyPackage(*ArchitecturePackages.JOB_HOMES)
                .and()
                .areDeclaredInClassesThat()
                .areAssignableTo(AbstractJsonJobHandler::class.java)
                .should()
                .notBeAnnotatedWith(Transactional::class.java)
                .because(
                    "AbstractJsonJobHandler.handle() is already @Transactional; annotating handlePayload creates nested-transaction surprises",
                )
        }

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
                .that()
                .resideInAnyPackage("${ArchitecturePackages.ROOT}..")
                .and()
                .haveSimpleNameEndingWith("Adapter")
                .and()
                .doNotHaveModifier(JavaModifier.ABSTRACT)
                .and()
                .resideOutsideOfPackages(ArchitecturePackages.PLATFORM_MOCK)
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
                .that()
                .resideInAnyPackage(ArchitecturePackages.PLATFORM_MOCK)
                .and(isSpringBean())
                .should()
                .beAnnotatedWith(Primary::class.java)
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
                .that()
                .resideInAnyPackage(ArchitecturePackages.PLATFORM_MOCK)
                .and(isSpringBean())
                .should(haveTestOrDevProfile())
                .because("ADR-022: Mock adapters must be scoped to test/dev profiles to prevent production activation")
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
                .that()
                .resideInAnyPackage(ArchitecturePackages.PLATFORM_INTEGRATION)
                .and()
                .haveSimpleNameEndingWith("Specifications")
                .and()
                .doNotHaveModifier(JavaModifier.ABSTRACT)
                .should()
                .resideInAnyPackage("${ArchitecturePackages.ROOT}.platform.integration..persistence.spec..")
                .allowEmptyShould(true)
                .because("ADR-022: Standard layout requires specifications at ..persistence.spec..")
        }

    /** Matches classes that are Spring-managed beans (@Component or @Service). */
    private fun isSpringBean(): DescribedPredicate<JavaClass> =
        DescribedPredicate.describe("is a Spring bean (@Component or @Service)") { clazz ->
            clazz.isAnnotatedWith(Component::class.java) || clazz.isAnnotatedWith(Service::class.java)
        }

    private fun beAnnotatedWithProfile(): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("be annotated with @Profile") {
            override fun check(
                clazz: JavaClass,
                events: ConditionEvents,
            ) {
                val hasProfile = clazz.isAnnotatedWith(Profile::class.java)
                if (!hasProfile) {
                    events.add(
                        SimpleConditionEvent.violated(
                            clazz,
                            "${clazz.name} is missing @Profile — production adapters must declare a profile to prevent test environment pollution",
                        ),
                    )
                }
            }
        }

    private fun haveTestOrDevProfile(): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("have @Profile value containing 'test' or 'dev'") {
            override fun check(
                clazz: JavaClass,
                events: ConditionEvents,
            ) {
                val profileAnnotation = clazz.tryGetAnnotationOfType(Profile::class.java)
                if (!profileAnnotation.isPresent) {
                    events.add(
                        SimpleConditionEvent.violated(
                            clazz,
                            "${clazz.name} is missing @Profile — mock adapters must have @Profile('test') or @Profile('test | dev')",
                        ),
                    )
                    return
                }
                val value =
                    profileAnnotation
                        .get()
                        .value
                        .joinToString("|")
                        .lowercase()
                if (!value.contains("test") && !value.contains("dev")) {
                    events.add(
                        SimpleConditionEvent.violated(
                            clazz,
                            "${clazz.name} has @Profile($value) which does not contain 'test' or 'dev' — mock adapters must target test/dev profiles",
                        ),
                    )
                }
            }
        }
}
