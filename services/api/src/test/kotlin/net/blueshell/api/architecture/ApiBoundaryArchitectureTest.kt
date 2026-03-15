package net.blueshell.api.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*
import jakarta.persistence.Entity
import net.blueshell.api.architecture.support.ArchJUnitTestBase
import net.blueshell.api.architecture.support.SignatureConditions
import net.blueshell.api.shared.model.Identifiable
import org.junit.jupiter.api.Test

/**
 * ArchUnit tests enforcing API boundary best practices.
 * Aligned with ADR-001, ADR-012.
 */
class ApiBoundaryArchitectureTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {

    @Test
    fun `controllers must not expose entities in method signatures`(): Unit =
        arch("Controllers must not expose persistence entities in method signatures") {

            val isEntityOrIdentifiable: DescribedPredicate<JavaClass> =
                DescribedPredicate.describe("be annotated with @Entity OR extend Identifiable") { jc ->
                    jc.hasAnnotationOrNull(Entity::class.java) || jc.isAssignableToOrNull(Identifiable::class.java)
                }

            methods()
                .that().areDeclaredInClassesThat().resideInAnyPackage(ArchitecturePackages.WEB)
                .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
                .and().arePublic()
                .should(SignatureConditions.notReferenceTypes(isEntityOrIdentifiable))
                .because("ADR-001: Controllers are API boundary - expose DTOs/responses, never persistence entities")
                .allowEmptyShould(true)
        }

    @Test
    fun `controllers must not depend on JPA or Hibernate types`(): Unit =
        arch("Controllers must not depend on JPA/Hibernate") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.WEB)
                .and().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
                .because("ADR-001: Web layer should not know about persistence technology")
        }

    @Test
    fun `controllers must not depend on Spring Data repositories`(): Unit =
        arch("Controllers must not import repositories") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.WEB)
                .and().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat()
                .resideInAnyPackage(ArchitecturePackages.REPOSITORY)
                .because("ADR-002: Controllers use CommandBus, not direct repository access")
        }

    @Test
    fun `entities implement Identifiable interface`(): Unit =
        arch("Entities must implement Identifiable") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.PERSISTENCE)
                .and().areAnnotatedWith(Entity::class.java)
                .should().beAssignableTo(Identifiable::class.java)
                .because("ADR-001: Entities should implement Identifiable for generic type-safe operations")
        }

    @Test
    fun `entities must not depend on Spring MVC or HTTP`(): Unit =
        arch("Entities must not depend on Spring Web") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.PERSISTENCE)
                .and().areAnnotatedWith(Entity::class.java)
                .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.web..",
                    "org.springframework.http.."
                )
                .because("ADR-001: Persistence layer must not know about web framework")
        }

    @Test
    fun `entities must not depend on Jackson`(): Unit =
        arch("Entities must not use Jackson annotations") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.PERSISTENCE)
                .and().areAnnotatedWith(Entity::class.java)
                .should().dependOnClassesThat().resideInAnyPackage("com.fasterxml.jackson..")
                .because("Jackson on entities causes lazy-loading and serialization issues - use DTOs instead")
        }

    @Test
    fun `web DTOs must not be entities`(): Unit =
        arch("DTOs must not be JPA entities") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.DTO)
                .should().beAnnotatedWith(Entity::class.java)
                .because("DTOs and entities serve different purposes - keep them separate")
        }

    @Test
    fun `commands must not be entities`(): Unit =
        arch("Commands must not be JPA entities") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.COMMAND)
                .and().haveSimpleNameEndingWith("Command")
                .should().beAnnotatedWith(Entity::class.java)
                .because("ADR-002: Commands are use case representations, not persistence models")
        }

    @Test
    fun `ACL adapters isolate external dependencies`(): Unit =
        arch("ACL adapters must be in platform integration layer") {
            classes()
                .that().haveSimpleNameEndingWith("Adapter")
                .and().areNotInterfaces()
                .and().resideInAnyPackage("${ArchitecturePackages.ROOT}..") // Within project only
                .should().resideInAnyPackage(ArchitecturePackages.PLATFORM_INTEGRATION)
                .because("ADR-019: ACL adapters protect domain from external system changes")
                .allowEmptyShould(true)
        }

    private fun JavaClass.hasAnnotationOrNull(annotation: Class<out Annotation>): Boolean =
        try {
            getAnnotationOfType(annotation) != null
        } catch (_: IllegalArgumentException) {
            false
        }

    private fun JavaClass.isAssignableToOrNull(type: Class<*>): Boolean =
        runCatching { type.isAssignableFrom(this.reflect()) }.getOrDefault(false)
}
