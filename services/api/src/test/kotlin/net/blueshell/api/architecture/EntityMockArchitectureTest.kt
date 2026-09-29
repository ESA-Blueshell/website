package net.blueshell.api.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import jakarta.persistence.Entity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.readText

/**
 * A unit test builds its entities with `Entities` rather than mocking them: a mocked entity
 * answers only what was stubbed, so a rule that reads one more field passes on a null the entity
 * could never hold.
 *
 * Read from the sources, since a mock of an entity leaves nothing in the bytecode an ArchUnit rule
 * can tell from any other use of its class. A `mock()` whose type is inferred passes unseen.
 */
class EntityMockArchitectureTest {
    private val entities =
        ClassFileImporter()
            .importPath(Paths.get("build/classes/kotlin/main"))
            .filter { it.isAnnotatedWith(Entity::class.java) }
            .map { it.simpleName }

    private val pattern =
        entities.joinToString("|").let {
            Regex("""\b(?:mock|mockk|spy|spyk)<($it)>|:\s*($it)\??\s*=\s*(?:mock|mockk)\b|(?:mock|mockk)\(($it)::class""")
        }

    private fun measured(): Map<String, Int> {
        val root = Paths.get("src/test/kotlin/net/blueshell/api")
        return Files.walk(root).use { paths ->
            paths
                .filter { it.extension == "kt" }
                .toList()
                .associate { root.relativize(it).toString() to pattern.findAll(it.readText()).count() }
                .filterValues { it > 0 }
        }
    }

    @Test
    fun `no unit test mocks an entity`() {
        assertThat(entities).describedAs("the entity classes this rule reads for").isNotEmpty

        assertThat(measured())
            .describedAs("build these entities with testsupport.Entities rather than mocking them")
            .isEmpty()
    }
}
