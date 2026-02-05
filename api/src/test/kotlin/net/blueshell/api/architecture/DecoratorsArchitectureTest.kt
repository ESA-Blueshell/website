package net.blueshell.api.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.persistence.Entity
import net.blueshell.api.architecture.ArchitecturePackages.CONTROLLER
import net.blueshell.api.architecture.ArchitecturePackages.DTO
import net.blueshell.api.architecture.support.DoNotIncludeTestSupport
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RestController

/**
 * ArchUnit: controller and DTO decoration expectations.
 */
@AnalyzeClasses(
    packages = [ArchitecturePackages.ROOT],
    importOptions = [ImportOption.DoNotIncludeTests::class, DoNotIncludeTestSupport::class]
)
class DecoratorsArchitectureTest {

    @ArchTest
    val controllersAreDecoratedWithRestController: ArchRule =
        classes()
            .that().resideInAnyPackage(CONTROLLER)
            .and().haveSimpleNameEndingWith("Controller")
            .should().beAnnotatedWith(RestController::class.java)
            .orShould().beAnnotatedWith(Controller::class.java)

    @ArchTest
    val controllersAreDecoratedWithTag: ArchRule =
        classes()
            .that().resideInAnyPackage(CONTROLLER)
            .and().haveSimpleNameEndingWith("Controller")
            .should().beAnnotatedWith(Tag::class.java)

    @ArchTest
    val dtosAreDecoratedWithSchema: ArchRule =
        classes()
            .that().resideInAnyPackage(DTO)
            .and().areTopLevelClasses()
            .should().beAnnotatedWith(Schema::class.java)

    @ArchTest
    val endpointsHaveAuthorizationDecorators: ArchRule =
        methods()
            .that().areDeclaredInClassesThat().resideInAnyPackage(CONTROLLER)
            .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
            .and().arePublic()
            .should().beAnnotatedWith(PreAuthorize::class.java)
            .orShould().beAnnotatedWith(PermitAll::class.java)

    // Best-practice add: DTOs must not accidentally become JPA entities
    @ArchTest
    val dtosMustNotBeEntities: ArchRule =
        noClasses()
            .that().resideInAnyPackage(DTO)
            .should().beAnnotatedWith(Entity::class.java)
            .because("DTOs must not be persistence entities; keep persistence in the model layer.")
}
