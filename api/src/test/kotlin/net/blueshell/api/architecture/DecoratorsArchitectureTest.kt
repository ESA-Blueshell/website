package net.blueshell.api.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.persistence.Entity
import net.blueshell.api.architecture.ArchitecturePackages.CONTROLLER
import net.blueshell.api.architecture.ArchitecturePackages.DTO
import net.blueshell.api.architecture.support.ArchJUnitTestBase
import org.junit.jupiter.api.Test
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RestController

class DecoratorsArchitectureTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {

    @Test
    fun `controllers are decorated with RestController or Controller`(): Unit =
        arch("Controllers must be web controllers") {
            classes()
                .that().resideInAnyPackage(CONTROLLER)
                .and().haveSimpleNameEndingWith("Controller")
                .should().beAnnotatedWith(RestController::class.java)
                .orShould().beAnnotatedWith(Controller::class.java)
        }

    @Test
    fun `controllers are decorated with OpenAPI Tag`(): Unit =
        arch("Controllers must have @Tag") {
            classes()
                .that().resideInAnyPackage(CONTROLLER)
                .and().haveSimpleNameEndingWith("Controller")
                .should().beAnnotatedWith(Tag::class.java)
        }

    @Test
    fun `dtos are decorated with Schema`(): Unit =
        arch("DTOs must have @Schema") {
            classes()
                .that().resideInAnyPackage(DTO)
                .and().areTopLevelClasses()
                .should().beAnnotatedWith(Schema::class.java)
        }

    @Test
    fun `endpoints are secured by PreAuthorize or PermitAll at method or class level`(): Unit =
        arch("Public controller methods must be secured") {
            methods()
                .that().areDeclaredInClassesThat().resideInAnyPackage(CONTROLLER)
                .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
                .and().arePublic()
                .and().haveNameNotContaining("$")
                .should(beSecuredByPreAuthorizeOrPermitAll())
                .because("Security should be explicit. Allow class-level security to reduce repetition.")
        }

    @Test
    fun `dtos must not be entities`(): Unit =
        arch("DTOs must not be JPA entities") {
            noClasses()
                .that().resideInAnyPackage(DTO)
                .should().beAnnotatedWith(Entity::class.java)
                .because("DTOs must not be persistence entities; keep persistence in the model layer.")
        }

    @Test
    fun `no web controllers outside controller package`(): Unit =
        arch("No @RestController/@Controller outside controller package") {
            noClasses()
                .that().resideOutsideOfPackage(CONTROLLER)
                .should().beAnnotatedWith(RestController::class.java)
                .orShould().beAnnotatedWith(Controller::class.java)
                .because("Avoid 'hidden controllers' in random packages; it makes scanning, security, and refactors harder.")
        }

    @Test
    fun `dtos must not be Spring components`(): Unit =
        arch("DTOs must be passive data carriers") {
            noClasses()
                .that().resideInAnyPackage(DTO)
                .should().beAnnotatedWith(Component::class.java)
                .orShould().beAnnotatedWith(Service::class.java)
                .orShould().beAnnotatedWith(Repository::class.java)
                .orShould().beAnnotatedWith(Controller::class.java)
                .because("DTOs should not be managed beans; keep them as simple data structures.")
        }

    private fun beSecuredByPreAuthorizeOrPermitAll(): ArchCondition<JavaMethod> =
        object : ArchCondition<JavaMethod>("be secured by @PreAuthorize/@PermitAll (method or class)") {
            override fun check(method: JavaMethod, events: ConditionEvents) {
                val ok =
                    method.getAnnotationOfTypeOrNull(PreAuthorize::class.java) != null ||
                            method.getAnnotationOfTypeOrNull(PermitAll::class.java) != null ||
                            method.owner.getAnnotationOfTypeOrNull(PreAuthorize::class.java) != null ||
                            method.owner.getAnnotationOfTypeOrNull(PermitAll::class.java) != null

                if (!ok) {
                    val msg =
                        "${method.fullName} should be secured via @PreAuthorize/@PermitAll on method or controller class"
                    events.add(SimpleConditionEvent.violated(method, msg))
                }
            }
        }


    private fun <A : Annotation> JavaMethod.getAnnotationOfTypeOrNull(annotationType: Class<A>): A? =
        try { getAnnotationOfType(annotationType) } catch (_: IllegalArgumentException) { null }

    private fun <A : Annotation> JavaClass.getAnnotationOfTypeOrNull(annotationType: Class<A>): A? =
        try { getAnnotationOfType(annotationType) } catch (_: IllegalArgumentException) { null }
}
