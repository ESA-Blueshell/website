package net.blueshell.api.architecture

import com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import net.blueshell.api.base.entity.BaseModel
import net.blueshell.api.architecture.ArchitecturePackages.CONTROLLER
import net.blueshell.api.architecture.ArchitecturePackages.MODEL
import net.blueshell.api.architecture.ArchitecturePackages.MODEL_CONVERTER
import net.blueshell.api.testsupport.DoNotIncludeTestSupport
import net.blueshell.api.testsupport.GenericsPredicates.assignableToGeneric
import net.blueshell.api.testsupport.ReturnTypeConditions.notHaveReturnType

import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import net.blueshell.api.base.entity.Identifiable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * ArchUnit: API boundary rules:
 * - Controllers must not leak entities (models) in return types.
 * - All model classes extend BaseModel (excluding converters).
 * - Controllers should not be in “mixed” packages.
 */
@AnalyzeClasses(
    packages = [ArchitecturePackages.ROOT],
    importOptions = [ImportOption.DoNotIncludeTests::class, DoNotIncludeTestSupport::class]
)
class ApiBoundaryArchitectureTest {

    @ArchTest
    val controllerMethodsDontReturnModels: ArchRule =
        methods()
            .that().areDeclaredInClassesThat().resideInAnyPackage(CONTROLLER)
            .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
            .and().arePublic()
            .should().notHaveRawReturnType(assignableTo(BaseModel::class.java))
            .andShould(notHaveReturnType(assignableToGeneric(Iterable::class.java, BaseModel::class.java)))
            .because("Controllers must return DTOs/resources, not persistence entities.")

    @ArchTest
    val modelsExtendIdentifiable: ArchRule =
        classes()
            .that().resideInAnyPackage(MODEL)
            .and().resideOutsideOfPackage(MODEL_CONVERTER)
            .should().beAssignableTo(Identifiable::class.java)
            .because("All model entities should share identity contract.")

    // Best-practice: web annotations must not appear in model layer
    @ArchTest
    val modelMustNotDependOnWebMvc: ArchRule =
        noClasses()
            .that().resideInAnyPackage(MODEL)
            .should().dependOnClassesThat().areAnnotatedWith(RestController::class.java)
            .orShould().dependOnClassesThat().areAnnotatedWith(RequestMapping::class.java)
            .because("Model layer must not depend on Spring MVC/web concerns.")
}
