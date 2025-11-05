package net.blueshell.api.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import net.blueshell.api.testsupport.DoNotIncludeFactory;
import net.blueshell.api.testsupport.DoNotIncludeTestSupport;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit: access restrictions between common app layers.
 */
@AnalyzeClasses(
        packages = "net.blueshell.api",
        importOptions = {ImportOption.DoNotIncludeTests.class, DoNotIncludeTestSupport.class, DoNotIncludeFactory.class}
)
public class AccessArchitectureTest {

    private static final String DTO = "net.blueshell.api.dto..";
    private static final String MODEL = "net.blueshell.api.model..";
    private static final String CONTROLLER = "net.blueshell.api.controller..";
    private static final String MAPPER = "net.blueshell.api.mapper..";
    private static final String VALIDATOR = "net.blueshell.api.validation..";
    private static final String SERVICE = "net.blueshell.api.service..";
    private static final String LISTENER = "net.blueshell.api.listener..";
    private static final String JPA_LISTENER = "net.blueshell.api.listener.jpa..";
    private static final String JOB = "net.blueshell.api.job..";
    private static final String REPOSITORY = "net.blueshell.api.repository..";

    @ArchTest
    public final ArchRule dtoOnlyAccessedAtApiBorder = classes()
            .that().resideInAnyPackage(DTO)
            .should().onlyBeAccessed().byAnyPackage(CONTROLLER, MAPPER, DTO, VALIDATOR);

    @ArchTest
    public final ArchRule modelsNotAccessedInDtos = noClasses()
            .that().resideInAnyPackage(DTO)
            .should().accessClassesThat().resideInAnyPackage(MODEL);

    @ArchTest
    public final ArchRule repositoryOnlyAccessedByServicesAndValidation = classes()
            .that().resideInAnyPackage(REPOSITORY)
            .should().onlyBeAccessed().byAnyPackage(SERVICE, REPOSITORY, VALIDATOR);

    @ArchTest
    public final ArchRule jobsOnlyAccessedFromListeners = classes()
            .that().resideInAnyPackage(JOB)
            .should().onlyBeAccessed().byAnyPackage(LISTENER, JOB);

    @ArchTest
    public final ArchRule jobsNotAccessedFromJpaListeners = noClasses()
            .that().resideInAnyPackage(JPA_LISTENER)
            .should().accessClassesThat().resideInAnyPackage(JOB);
}
