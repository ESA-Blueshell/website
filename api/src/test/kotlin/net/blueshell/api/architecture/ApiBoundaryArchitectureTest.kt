package net.blueshell.api.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*
import jakarta.persistence.Entity
import net.blueshell.api.architecture.ArchitecturePackages.CONTROLLER
import net.blueshell.api.architecture.ArchitecturePackages.MODEL
import net.blueshell.api.architecture.ArchitecturePackages.MODEL_CONVERTER
import net.blueshell.api.architecture.support.ArchJUnitTestBase
import net.blueshell.api.architecture.support.SignatureConditions
import net.blueshell.api.shared.model.Identifiable
import org.junit.jupiter.api.Test

class ApiBoundaryArchitectureTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {

    @Test
    fun `controllers must not expose entities in method signatures (including generics)`() =
        arch("Controllers must not expose persistence entities in method signatures") {

            val isEntityOrBaseModel: DescribedPredicate<JavaClass> =
                DescribedPredicate.describe("be annotated with @Entity OR be assignable to BaseModel") { jc ->
                    jc.hasAnnotationOrNull(Entity::class.java) || jc.isAssignableToOrNull(Identifiable::class.java)
                }

            methods()
                .that().areDeclaredInClassesThat().resideInAnyPackage(CONTROLLER)
                .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
                .and().arePublic()
                .should(SignatureConditions.notReferenceTypes(isEntityOrBaseModel))
                .because("Controllers are an API boundary: expose DTOs/resources, never persistence types (even nested in wrappers).")
        }

    @Test
    fun `controllers must not depend on JPA or Hibernate types`(): Unit =
        arch("Controllers must not depend on JPA/Hibernate") {
            noClasses()
                .that().resideInAnyPackage(CONTROLLER)
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..", "..repository..")
        }

    @Test
    fun `models extend Identifiable`(): Unit =
        arch("Entities must implement Identifiable") {
            classes()
                .that().resideInAnyPackage(MODEL)
                .and().resideOutsideOfPackage(MODEL_CONVERTER)
                .and().areAnnotatedWith(Entity::class.java)
                .should().beAssignableTo(Identifiable::class.java)
        }

    @Test
    fun `model must not depend on Spring MVC or HTTP`(): Unit =
        arch("Model must not depend on Spring Web") {
            noClasses()
                .that().resideInAnyPackage(MODEL)
                .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.web..",
                    "org.springframework.http.."
                )
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
