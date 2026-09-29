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
import net.blueshell.api.shared.credentials.WhenCredentialsMissing
import net.blueshell.api.shared.credentials.WhenCredentialsSet
import org.junit.jupiter.api.Test
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * ArchUnit consistency rules for the platform modules, aligned with ADR-019 and ADR-022. Each
 * rule states itself; the letters group them: A job handler structure, B adapter profiles,
 * C specification placement.
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
                    "AbstractJsonJobHandler.handle() is already @Transactional; annotating " +
                        "handlePayload creates nested-transaction surprises",
                )
        }

    /**
     * B1: An adapter says which credentials switch it on, or which ones it stands in for.
     *
     * Rationale: a vendor client and its in-memory stand-in are switched by one rule, so exactly
     * one of the two exists wherever the api runs. An adapter carrying neither would run beside
     * its twin, or in place of it.
     */
    @Test
    fun `adapters are switched by their credentials`(): Unit =
        arch("Spring bean *Adapter classes carry @WhenCredentialsSet or @WhenCredentialsMissing") {
            classes()
                .that()
                .haveSimpleNameEndingWith("Adapter")
                .and()
                .doNotHaveModifier(JavaModifier.ABSTRACT)
                .and(isSpringBean())
                .should(beSwitchedByCredentials())
                .because("API ADR-019: a vendor client is real where its credentials are set, and its stand-in otherwise")
        }

    /**
     * B2: A stand-in names the same credentials as the real implementation of its port, so the
     * two can never both be missing or both be present.
     */
    @Test
    fun `stand-ins mirror the credentials of their real twin`(): Unit =
        arch("A @WhenCredentialsMissing bean has a @WhenCredentialsSet twin on the same port with the same properties") {
            classes()
                .that()
                .areAnnotatedWith(WhenCredentialsMissing::class.java)
                .should(haveARealTwin())
                .because("API ADR-019: exactly one of a vendor client and its stand-in exists")
        }

    /** Matches classes that are Spring-managed beans (@Component or @Service). */
    private fun isSpringBean(): DescribedPredicate<JavaClass> =
        DescribedPredicate.describe("is a Spring bean (@Component or @Service)") { clazz ->
            clazz.isAnnotatedWith(Component::class.java) || clazz.isAnnotatedWith(Service::class.java)
        }

    private fun beSwitchedByCredentials(): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("be annotated with @WhenCredentialsSet or @WhenCredentialsMissing") {
            override fun check(
                clazz: JavaClass,
                events: ConditionEvents,
            ) {
                if (!clazz.isAnnotatedWith(WhenCredentialsSet::class.java) && !clazz.isAnnotatedWith(WhenCredentialsMissing::class.java)) {
                    events.add(SimpleConditionEvent.violated(clazz, "${clazz.name} names no credentials"))
                }
            }
        }

    private fun haveARealTwin(): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("have a real twin with the same credentials") {
            override fun check(
                clazz: JavaClass,
                events: ConditionEvents,
            ) {
                val wanted = clazz.getAnnotationOfType(WhenCredentialsMissing::class.java).properties.toSet()
                val ports = clazz.allRawInterfaces.filter { it.packageName.startsWith(ArchitecturePackages.ROOT) }
                val twin =
                    ports.flatMap { it.allSubclasses }.any { real ->
                        real.isAnnotatedWith(WhenCredentialsSet::class.java) &&
                            real.getAnnotationOfType(WhenCredentialsSet::class.java).properties.toSet() == wanted
                    }
                if (!twin) {
                    events.add(SimpleConditionEvent.violated(clazz, "${clazz.name} has no real twin switched on $wanted"))
                }
            }
        }
}
