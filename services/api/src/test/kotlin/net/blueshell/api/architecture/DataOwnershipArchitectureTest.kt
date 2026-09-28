package net.blueshell.api.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import net.blueshell.api.architecture.support.ArchModules
import net.blueshell.api.architecture.support.DoNotIncludeAotGenerated
import net.blueshell.api.architecture.support.DoNotIncludeFactory
import net.blueshell.api.architecture.support.DoNotIncludeTestSources
import net.blueshell.api.architecture.support.DoNotIncludeTestSupport

/**
 * Api ADR-018: a module owns its persistence, and another module reads that data through the
 * owner's services. Entities are shared on purpose; repositories are not.
 */
@AnalyzeClasses(
    packages = [ArchModules.BASE],
    importOptions = [
        ImportOption.DoNotIncludeTests::class,
        DoNotIncludeTestSources::class,
        DoNotIncludeTestSupport::class,
        DoNotIncludeFactory::class,
        DoNotIncludeAotGenerated::class,
    ],
)
class DataOwnershipArchitectureTest {
    @ArchTest
    fun `no class reaches a repository of a module it is not in`(classes: JavaClasses) {
        classes()
            .should(reachOnlyItsOwnRepositories())
            .because("api ADR-018: a module reaches another module through its services, not its repositories")
            .check(classes)
    }

    private fun reachOnlyItsOwnRepositories() =
        object : ArchCondition<JavaClass>("reach no other module's repository") {
            override fun check(
                item: JavaClass,
                events: ConditionEvents,
            ) {
                item.directDependenciesFromSelf
                    .map { it.targetClass }
                    .filter { ArchModules.reachesForeignRepository(item.packageName, it.packageName, it.simpleName) }
                    .distinct()
                    .forEach { target ->
                        events.add(SimpleConditionEvent.violated(item, "${item.name} reaches ${target.name}"))
                    }
            }
        }
}
