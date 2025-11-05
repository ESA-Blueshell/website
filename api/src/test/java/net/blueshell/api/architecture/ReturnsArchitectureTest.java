package net.blueshell.api.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.testsupport.DoNotIncludeTestSupport;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * ArchUnit: all model classes should extend BaseModel (excluding converters).
 */
@AnalyzeClasses(
        packages = "net.blueshell.api",
        importOptions = {ImportOption.DoNotIncludeTests.class, DoNotIncludeTestSupport.class}
)
public class ReturnsArchitectureTest {

    private static final String MODEL = "net.blueshell.api.model..";
    private static final String CONVERTER = "net.blueshell.api.model.converter..";

    @ArchTest
    public final ArchRule modelsExtendBaseModel = classes()
            .that().resideInAnyPackage(MODEL)
            .and().resideOutsideOfPackage(CONVERTER)
            .should().beAssignableTo(BaseModel.class);
}
