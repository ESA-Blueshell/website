package net.blueshell.api.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import net.blueshell.api.testsupport.DoNotIncludeTestSupport;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * ArchUnit: controller and DTO decoration expectations.
 */
@AnalyzeClasses(
        packages = "net.blueshell.api",
        importOptions = {ImportOption.DoNotIncludeTests.class, DoNotIncludeTestSupport.class}
)
public class DecoratorsArchitectureTest {

    private static final String DTO = "net.blueshell.api.dto..";
    private static final String CONTROLLER = "net.blueshell.api.controller..";

    @ArchTest
    public final ArchRule controllersAreDecoratedWithRestController = classes()
            .that().resideInAnyPackage(CONTROLLER)
            .and().haveSimpleNameEndingWith("Controller")
            .should().beAnnotatedWith(RestController.class)
            .orShould().beAnnotatedWith(Controller.class);

    @ArchTest
    public final ArchRule controllersAreDecoratedWithTag = classes()
            .that().resideInAnyPackage(CONTROLLER)
            .and().haveSimpleNameEndingWith("Controller")
            .should().beAnnotatedWith(Tag.class);

    @ArchTest
    public final ArchRule DTOsAreDecoratedWithSchema = classes()
            .that().resideInAnyPackage(DTO)
            .should().beAnnotatedWith(Schema.class);

    @ArchTest
    public final ArchRule endpointsHaveAuthorizationDecorators = methods()
            .that().areDeclaredInClassesThat().resideInAnyPackage(CONTROLLER)
            .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
            .and().arePublic()
            .should().beAnnotatedWith(PreAuthorize.class)
            .orShould().beAnnotatedWith(PermitAll.class);
}
