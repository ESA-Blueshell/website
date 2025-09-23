package net.blueshell.api.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import net.blueshell.api.base.BaseDTO;
import net.blueshell.api.testsupport.DoNotIncludeTestSupport;
import org.springframework.data.domain.Page;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
/**
 * Ensures that only service-layer classes may depend on repository-layer classes.
 * Scans application classes (tests excluded).
 */
@AnalyzeClasses(
        packages = "net.blueshell.api",
        importOptions = {ImportOption.DoNotIncludeTests.class, DoNotIncludeTestSupport.class}
)
public class ReturnsArchitectureTest {

    private static final String DTO = "net.blueshell.api.dto..";
    private static final String CONTROLLER = "net.blueshell.api.controller..";
    private static final String MAPPER = "net.blueshell.api.mapper..";
    private static final String VALIDATOR = "net.blueshell.api.validation..";
    private static final String SERVICE = "net.blueshell.api.service..";
    private static final String REPOSITORY = "net.blueshell.api.repository..";
    private static final String VALIDATION = "net.blueshell.api.validation..";

    @ArchTest
    public final ArchRule controllerMethodsOnlyReturnDTOs = methods()
            .that().areDeclaredInClassesThat().resideInAnyPackage(CONTROLLER)
            .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
            .and().arePublic()
            .should().haveRawReturnType(void.class)
            .orShould().haveRawReturnType(assignableTo(BaseDTO.class))
            .orShould().haveRawReturnType(Page.class)
            .orShould().haveRawReturnType(List.class);
}
