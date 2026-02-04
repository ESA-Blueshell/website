package net.blueshell.api.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import net.blueshell.api.base.entity.BaseModel;
import net.blueshell.api.testsupport.DoNotIncludeTestSupport;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static net.blueshell.api.testsupport.GenericsPredicates.assignableToGeneric;
import static net.blueshell.api.testsupport.ReturnTypeConditions.notHaveReturnType;

/**
 * ArchUnit: controller methods must not return model entities nor collections of them.
 */
@AnalyzeClasses(
        packages = "net.blueshell.api",
        importOptions = {ImportOption.DoNotIncludeTests.class, DoNotIncludeTestSupport.class}
)
public class ExtendsArchitectureTest {

    private static final String CONTROLLER = "net.blueshell.api.controller..";

    @ArchTest
    public final ArchRule controllersMethodsDontReturnModels = methods()
            .that().areDeclaredInClassesThat().resideInAnyPackage(CONTROLLER)
            .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
            .and().arePublic()
            .should().notHaveRawReturnType(assignableTo(BaseModel.class))
            .andShould(notHaveReturnType(assignableToGeneric(Iterable.class, BaseModel.class)));
}
