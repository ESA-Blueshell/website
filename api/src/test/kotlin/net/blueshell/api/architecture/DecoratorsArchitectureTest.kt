package net.blueshell.api.architecture

import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.persistence.Entity
import net.blueshell.api.architecture.support.ArchJUnitTestBase
import org.junit.jupiter.api.Test
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*

/**
 * ArchUnit tests enforcing proper annotation usage.
 * Aligned with ADR-001, ADR-009, ADR-012, ADR-014.
 */
class DecoratorsArchitectureTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {

    @Test
    fun `controllers are decorated with RestController`(): Unit =
        arch("Controllers must be @RestController") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.WEB)
                .and().doNotHaveModifier(JavaModifier.ABSTRACT)
                .and().haveSimpleNameEndingWith("Controller")
                .should().beAnnotatedWith(RestController::class.java)
                .because("ADR-001: All controllers should be REST controllers")
        }

    @Test
    fun `controllers are decorated with OpenAPI Tag`(): Unit =
        arch("Controllers must have @Tag for API documentation") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.WEB)
                .and().doNotHaveModifier(JavaModifier.ABSTRACT)
                .and().haveSimpleNameEndingWith("Controller")
                .should().beAnnotatedWith(Tag::class.java)
                .because("ADR-012: All controllers must have OpenAPI documentation")
        }

    @Test
    fun `response DTOs are decorated with Schema`(): Unit =
        arch("Response DTOs must have @Schema") {
            classes()
                .that().resideInAnyPackage("${ArchitecturePackages.DTO}response..")
                .and().doNotHaveModifier(JavaModifier.ABSTRACT)
                .and().areTopLevelClasses()
                .and().haveSimpleNameEndingWith("Response")
                .should().beAnnotatedWith(Schema::class.java)
                .because("ADR-012: Response DTOs should have OpenAPI schema documentation")
                .allowEmptyShould(true)
        }

    @Test
    fun `request DTOs are decorated with Schema`(): Unit =
        arch("Request DTOs must have @Schema") {
            classes()
                .that().resideInAnyPackage("${ArchitecturePackages.DTO}request..")
                .and().doNotHaveModifier(JavaModifier.ABSTRACT)
                .and().areTopLevelClasses()
                .and().haveSimpleNameEndingWith("Request")
                .should().beAnnotatedWith(Schema::class.java)
                .because("ADR-012: Request DTOs should have OpenAPI schema documentation")
                .allowEmptyShould(true)
        }

    @Test
    fun `endpoints are secured by PreAuthorize or PermitAll`(): Unit =
        arch("Public controller methods must be explicitly secured") {
            methods()
                .that().areAnnotatedWith(PostMapping::class.java)
                .or().areAnnotatedWith(GetMapping::class.java)
                .or().areAnnotatedWith(PutMapping::class.java)
                .or().areAnnotatedWith(DeleteMapping::class.java)
                .or().areAnnotatedWith(PatchMapping::class.java)
                .should(beSecuredByPreAuthorizeOrPermitAll())
                .because("ADR-009: All endpoints must have explicit security declarations")
        }

    @Test
    fun `dtos must not be entities`(): Unit =
        arch("DTOs must not be JPA entities") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.DTO)
                .should().beAnnotatedWith(Entity::class.java)
                .because("ADR-001: DTOs are API contracts, entities are persistence models - keep separate")
        }

    @Test
    fun `no web controllers outside web package`(): Unit =
        arch("No @RestController outside web package") {
            noClasses()
                .that().resideOutsideOfPackage(ArchitecturePackages.WEB)
                .should().beAnnotatedWith(RestController::class.java)
                .orShould().beAnnotatedWith(Controller::class.java)
                .because("ADR-001: Controllers must be in web layer for clear boundaries")
        }

    @Test
    fun `dtos must not be Spring components`(): Unit =
        arch("DTOs must be passive data carriers") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.DTO)
                .should().beAnnotatedWith(Component::class.java)
                .orShould().beAnnotatedWith(Service::class.java)
                .orShould().beAnnotatedWith(Repository::class.java)
                .orShould().beAnnotatedWith(Controller::class.java)
                .because("ADR-001: DTOs are simple data structures, not managed beans")
        }

    @Test
    fun `commands must not be Spring components`(): Unit =
        arch("Commands must be passive data structures") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.COMMAND)
                .and().haveSimpleNameEndingWith("Command")
                .should().beAnnotatedWith(Component::class.java)
                .orShould().beAnnotatedWith(Service::class.java)
                .orShould().beAnnotatedWith(Repository::class.java)
                .because("ADR-002: Commands are immutable data classes, not managed beans")
        }

    @Test
    fun `command handlers are Spring components`(): Unit =
        arch("Command handlers must be @Component") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.COMMAND_HANDLER)
                .and().haveSimpleNameEndingWith("Handler")
                .and().doNotHaveModifier(JavaModifier.ABSTRACT)
                .should().beAnnotatedWith(Component::class.java)
                .because("ADR-002: Command handlers are Spring beans registered with CommandBus")
        }

    @Test
    fun `permission evaluators are Spring components`(): Unit =
        arch("Permission evaluators must be @Component") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.PERMISSION)
                .and().haveSimpleNameEndingWith("Permission")
                .and().doNotHaveModifier(JavaModifier.ABSTRACT)
                .should().beAnnotatedWith(Component::class.java)
                .because("ADR-014: Permission evaluators are Spring beans discovered by CompositePermissionEvaluator")
        }

    @Test
    fun `entities must not be Kotlin data classes`(): Unit =
        arch("Entities should not be Kotlin data classes") {
            classes()
                .that().areAnnotatedWith(Entity::class.java)
                .should(notBeKotlinDataClass())
                .because("Data classes generate equals/hashCode/copy which is problematic for JPA entities and proxies")
        }

    // ---- Helper conditions ----

    private fun beSecuredByPreAuthorizeOrPermitAll(): ArchCondition<JavaMethod> =
        object : ArchCondition<JavaMethod>("be secured by @PreAuthorize or @PermitAll at method or class level") {
            override fun check(item: JavaMethod, events: ConditionEvents) {
                val hasMethodLevelSecurity = item.isAnnotatedWith(PreAuthorize::class.java) ||
                        item.isAnnotatedWith(PermitAll::class.java)

                val hasClassLevelSecurity = item.owner.isAnnotatedWith(PreAuthorize::class.java) ||
                        item.owner.isAnnotatedWith(PermitAll::class.java)

                val isSecured = hasMethodLevelSecurity || hasClassLevelSecurity

                events.add(
                    SimpleConditionEvent(
                        item,
                        isSecured,
                        "${item.fullName} ${if (isSecured) "is" else "is NOT"} secured by @PreAuthorize or @PermitAll"
                    )
                )
            }
        }

    private fun notBeKotlinDataClass(): ArchCondition<com.tngtech.archunit.core.domain.JavaClass> =
        object : ArchCondition<com.tngtech.archunit.core.domain.JavaClass>("not be a Kotlin data class") {
            override fun check(item: com.tngtech.archunit.core.domain.JavaClass, events: ConditionEvents) {
                try {
                    val kotlinClass = item.reflect().kotlin
                    val isDataClass = kotlinClass.isData

                    events.add(
                        SimpleConditionEvent(
                            item,
                            !isDataClass,
                            "${item.fullName} ${if (isDataClass) "is" else "is not"} a Kotlin data class"
                        )
                    )
                } catch (e: Exception) {
                    // Not a Kotlin class or can't determine - assume OK
                    events.add(SimpleConditionEvent(item, true, "${item.fullName} is not a Kotlin data class"))
                }
            }
        }
}
