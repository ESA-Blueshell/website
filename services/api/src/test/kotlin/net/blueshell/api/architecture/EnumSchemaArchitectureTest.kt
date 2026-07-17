package net.blueshell.api.architecture

import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*
import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.architecture.support.ArchJUnitTestBase
import org.junit.jupiter.api.Test

/**
 * ArchUnit tests enforcing OpenAPI schema documentation on API-exposed enums.
 * Aligned with ADR-001 (API boundary best practices).
 *
 * Rule scope: All enum classes in the shared.enums package must be annotated with @Schema
 * to ensure the OpenAPI contract is complete and intentional, not reliant on springdoc auto-detection.
 *
 * Rationale: Enums in `net.blueshell.api.shared.enums` are part of the shared kernel and
 * cross the API boundary when used in request/response DTOs. Explicit @Schema annotation
 * guarantees the generated OpenAPI spec and frontend client enums are intentional.
 */
class EnumSchemaArchitectureTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {

    @Test
    fun `shared enums must be annotated with @Schema`(): Unit =
        arch("All shared enums must have @Schema annotation for OpenAPI documentation") {
            classes()
                .that().resideInAnyPackage(ArchitecturePackages.SHARED_ENUM)
                .and().areEnums()
                .should().beAnnotatedWith(Schema::class.java)
                .because("ADR-001: API boundary must be explicit - enums crossing API boundary must have @Schema to ensure intentional OpenAPI contract generation")
        }
}
