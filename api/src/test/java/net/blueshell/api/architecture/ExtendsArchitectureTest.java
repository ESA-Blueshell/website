package net.blueshell.api.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.testsupport.DoNotIncludeTestSupport;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static net.blueshell.api.architecture.GenericsPredicates.assignableToGeneric;
import static net.blueshell.api.testsupport.ReturnTypeConditions.notHaveReturnType;

/**
 * Ensures that only service-layer classes may depend on repository-layer classes.
 * Scans application classes (tests excluded).
 */
@AnalyzeClasses(
        packages = "net.blueshell.api",
        importOptions = {ImportOption.DoNotIncludeTests.class, DoNotIncludeTestSupport.class}
)
public class ExtendsArchitectureTest {

    private static final String DTO = "net.blueshell.api.dto..";
    private static final String CONTROLLER = "net.blueshell.api.controller..";
    private static final String MAPPER = "net.blueshell.api.mapper..";
    private static final String VALIDATOR = "net.blueshell.api.validation..";
    private static final String SERVICE = "net.blueshell.api.service..";
    private static final String REPOSITORY = "net.blueshell.api.repository..";
    private static final String VALIDATION = "net.blueshell.api.validation..";

    @ArchTest
    public final ArchRule controllersMethodsDontReturnModels = methods()
            .that().areDeclaredInClassesThat().resideInAnyPackage(CONTROLLER)
            .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
            .and().arePublic()
            .should().notHaveRawReturnType(assignableTo(BaseModel.class))
            .andShould(notHaveReturnType(assignableToGeneric(Iterable.class, BaseModel.class)));
}
