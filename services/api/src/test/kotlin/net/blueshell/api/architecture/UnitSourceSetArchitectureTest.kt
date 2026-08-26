package net.blueshell.api.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Guards the boundary that testing ADR-001 draws: a unit test runs without a
 * Spring context.
 *
 * This cannot extend [net.blueshell.api.architecture.support.ArchJUnitTestBase],
 * whose import options deliberately exclude test sources — the classes this rule
 * is about are exactly the ones that base class filters out.
 *
 * The check is on the annotation *and* on inheritance: six classes reached a
 * Spring context through `ServiceTestSupport` rather than by naming
 * `@SpringBootTest` themselves, which is why a textual search under-reported the
 * problem by six when the source sets were realigned.
 */
class UnitSourceSetArchitectureTest {

    private val springTestAnnotations = setOf(
        "org.springframework.boot.test.context.SpringBootTest",
        "org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest",
        "org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest",
    )

    /**
     * Runs only when explicitly invoked via the `openApiGenTest` task, which reads
     * `sourceSets["test"]`, and is excluded from the `test` task by its tag. It
     * therefore never contaminates the unit run and must stay where its task can
     * find it.
     */
    private val exemptions = setOf("net.blueshell.api.platform.config.OpenApiSpecGeneratorTest")

    @Test
    fun `no unit test may require a Spring context`() {
        val classesDir = Paths.get("build/classes/kotlin/test")
        assertThat(Files.isDirectory(classesDir))
            .describedAs("compiled unit test classes at %s — the rule cannot run without them", classesDir.toAbsolutePath())
            .isTrue()

        val offenders = ClassFileImporter()
            .importPath(classesDir)
            .filter { it.name !in exemptions }
            .filter { candidate ->
                generateSequence(candidate) { it.rawSuperclass.orElse(null) }
                    .any { cls -> cls.annotations.any { it.rawType.name in springTestAnnotations } }
            }
            .map { it.name }
            .sorted()

        assertThat(offenders)
            .describedAs(
                "testing ADR-001: a unit test runs without a Spring context. These need " +
                    "@SpringBootTest, directly or through a base class, so they belong in " +
                    "src/integrationTest",
            )
            .isEmpty()
    }
}
