package net.blueshell.acceptance

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name

/**
 * The features README forbids transport in the feature files, and three of the ten broke the
 * rule anyway. A documented rule nothing checks returns quietly, because a leaky scenario
 * passes — so the rule runs.
 *
 * Needs no stack and no database. The patterns are a short list of certainties rather than a
 * clever one: a guard that cries wolf gets switched off, and then the rule is unenforced.
 */
@Tag("system")
class FeaturesSayNoTransportTest {

    private data class Rule(val what: String, val pattern: Regex)

    private val rules = listOf(
        Rule(
            "an HTTP status",
            Regex("""\b(200|201|202|204|301|302|400|401|403|404|409|422|429|500|502|503)\b"""),
        ),
        Rule("the word \"status\"", Regex("""\bstatus(es)?\b""", RegexOption.IGNORE_CASE)),
        Rule("the word \"HTTP\"", Regex("""\bHTTP\b""", RegexOption.IGNORE_CASE)),
        Rule("a request succeeding rather than an outcome", Regex("""\bthe request succeeds\b""", RegexOption.IGNORE_CASE)),
        Rule("a refusal named by its status", Regex("""\b(is|are) forbidden\b""", RegexOption.IGNORE_CASE)),
        Rule("a refusal named by its kind", Regex("""\brefused as\b""", RegexOption.IGNORE_CASE)),
        Rule("an error code", Regex("""\b[A-Z][A-Za-z]*Ids?\b""")),
        Rule("a cookie or a header", Regex("""\b(cookie|header)s?\b""", RegexOption.IGNORE_CASE)),
        Rule("SQL", Regex("""\b(SELECT|INSERT INTO|DELETE FROM|UPDATE\s+\w+\s+SET|WHERE|JOIN)\b""")),
    )

    /** Scenario titles and steps. Prose under `Feature:` explains the split and may name a test class. */
    private val asserting = Regex("""^\s*(Scenario|Scenario Outline|Given|When|Then|And|But|\*)\b""")

    @Test
    fun `no feature file asserts transport`() {
        val features = featureFiles()
        assertThat(features).describedAs("feature files to check").isNotEmpty()

        val breaches = features.flatMap { file ->
            Files.readAllLines(file).withIndex().flatMap { (i, line) ->
                if (!asserting.containsMatchIn(line)) {
                    emptyList()
                } else {
                    rules.mapNotNull { rule ->
                        rule.pattern.find(line)?.let { hit ->
                            "${file.name}:${i + 1} says ${rule.what} — \"${hit.value}\"\n      ${line.trim()}"
                        }
                    }
                }
            }
        }

        assertThat(breaches).describedAs(
            """
            |A feature file is a specification the association agreed to, not a record of what
            |HTTP answered. The README beside these files states the rule:
            |
            |    No transport in the feature files. Status codes, cookies, headers, paths and
            |    SQL live in step definitions.
            |
            |Say what the person or the association got. A status code, an error code and a
            |field name belong to the integration test for that endpoint, which asserts them
            |faster and says which layer refused.
            """.trimMargin(),
        ).isEmpty()
    }

    private fun featureFiles(): List<Path> {
        val dir = requireNotNull(javaClass.classLoader.getResource("features")) {
            "The features directory is not on the test classpath"
        }
        return Files.list(Path.of(dir.toURI())).use { paths ->
            paths.filter { it.extension == "feature" }.sorted().toList()
        }
    }
}
