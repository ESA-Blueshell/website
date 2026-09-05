package net.blueshell.api.architecture

import com.tngtech.archunit.core.domain.JavaClass
import net.blueshell.api.architecture.support.ArchJUnitTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Every glob in [ArchitecturePackages] names a package that holds at least one class.
 *
 * A rule built on a glob that matches nothing passes without checking anything, and it passes
 * quietly: the suite reports it green, so a rule that stopped applying looks exactly like a rule
 * nobody violates. That is what the architecture ADR-003 flattening did to nineteen of these.
 *
 * ArchUnit's own `allowEmptyShould(false)` — the default — only guards the classes a rule
 * *selects*. Most of the stale constants sat on the far side instead, in `dependOnClassesThat`,
 * `onlyBeAccessed().byAnyPackage` or a `resideOutsideOfPackages` exemption, where an empty match
 * makes the rule permissive and ArchUnit never looks. Checking the constant itself catches both
 * positions, so the two mechanisms are kept together rather than one instead of the other.
 *
 * A constant with no user is not exempt: it is the vocabulary the next rule gets written in, and
 * inheriting a dead one is how this recurs.
 */
class PackageConstantsArchitectureTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {

    @Test
    fun `every package constant matches at least one class`() {
        val empty = globs()
            .filter { (_, glob) -> importedClasses.none { JavaClass.Predicates.resideInAPackage(glob).test(it) } }
            .map { (name, glob) -> "$name = $glob" }
            .sorted()

        assertThat(empty)
            .describedAs(
                "these globs match no class, so every rule built on them is vacuous. Point each at " +
                    "the package architecture ADR-003 actually puts those types in, or delete it " +
                    "along with the rules that read it",
            )
            .isEmpty()
    }

    /** Every constant's globs as `<name> to <glob>`; an array constant contributes one pair per element. */
    private fun globs(): List<Pair<String, String>> =
        ArchitecturePackages::class.java.declaredFields.flatMap { field ->
            field.isAccessible = true
            when (val value = field.get(ArchitecturePackages)) {
                is String -> listOf(field.name to value)
                is Array<*> -> value.filterIsInstance<String>().map { field.name to it }
                else -> emptyList()
            }
        }
}
