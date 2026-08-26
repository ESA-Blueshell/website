package net.blueshell.api.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import net.blueshell.api.architecture.support.DoNotIncludeAotGenerated
import net.blueshell.api.architecture.support.DoNotIncludeFactory
import net.blueshell.api.architecture.support.DoNotIncludeTestSources
import net.blueshell.api.architecture.support.DoNotIncludeTestSupport

/**
 * ArchUnit tests enforcing ADR-018: Data Ownership in Modular Monolith
 *
 * Key Rules:
 * - Cross-domain data access via services, NOT direct repository access
 * - Each domain owns its persistence layer
 * - No direct repository imports across domain boundaries
 */
@AnalyzeClasses(
    packages = ["net.blueshell.api"],
    importOptions = [
        ImportOption.DoNotIncludeTests::class,
        DoNotIncludeTestSources::class,
        DoNotIncludeTestSupport::class,
        DoNotIncludeFactory::class,
        DoNotIncludeAotGenerated::class,
    ]
)
class DataOwnershipArchitectureTest {

    /**
     * ADR-018 Violation 1: Listeners accessing repositories from other domains
     *
     * Example violation fixed in Phase 1:
     * - User domain listener importing CommitteeMemberRepository
     *
     * Fix: Use CommitteeMemberService instead
     */
    @ArchTest
    fun `listeners should not access repositories from other domains`(classes: JavaClasses) {
        // User domain listeners should not access Committee repositories
        noClasses()
            .that().resideInAPackage("..domain.user.application.listener..")
            .should().dependOnClassesThat()
            .resideInAPackage("..domain.committee.persistence.repository..")
            .allowEmptyShould(true)
            .check(classes)

        // Committee domain listeners should not access User repositories
        noClasses()
            .that().resideInAPackage("..domain.committee.application.listener..")
            .should().dependOnClassesThat()
            .resideInAPackage("..domain.user.persistence.repository..")
            .allowEmptyShould(true)
            .check(classes)

        // Event domain listeners should not access Survey repositories
        noClasses()
            .that().resideInAPackage("..domain.event.application.listener..")
            .should().dependOnClassesThat()
            .resideInAPackage("..domain.survey.persistence.repository..")
            .allowEmptyShould(true)
            .check(classes)
    }

    /**
     * ADR-018 Violation 2: Command handlers accessing repositories from other domains
     *
     * Example violation fixed in Phase 1:
     * - EventSignUpCommandHandlers importing QuestionRepository from Survey domain
     *
     * Fix: Use QuestionService instead
     */
    @ArchTest
    fun `command handlers should not access repositories from other domains`(classes: JavaClasses) {
        // Event domain application code should not access Survey repositories.
        // Widened from ..application.command.. when the event handlers became use
        // cases: the rule is about which repositories event code may reach, not
        // about the package the caller happens to sit in.
        noClasses()
            .that().resideInAPackage("..domain.event.application..")
            .should().dependOnClassesThat()
            .resideInAPackage("..domain.survey.persistence.repository..")
            .check(classes)

        // Survey domain command handlers should not access Event repositories
        noClasses()
            .that().resideInAPackage("..domain.survey.application.command..")
            .should().dependOnClassesThat()
            .resideInAPackage("..domain.event.persistence.repository..")
            .check(classes)
    }

    /**
     * ADR-018 Violation 3: Web validators accessing repositories directly
     *
     * Example violation fixed in Phase 1:
     * - ValidAnswerValidator importing QuestionRepository
     *
     * Fix: Use QuestionService instead
     * Rationale: ADR-003 says web validators should not access repositories
     */
    @ArchTest
    fun `web validators should use services not repositories`(classes: JavaClasses) {
        noClasses()
            .that().resideInAPackage("..domain.*.web.validation..")
            .should().dependOnClassesThat()
            .resideInAPackage("..persistence.repository..")
            .because("ADR-003 and ADR-018: Web validators should access data via services, not repositories")
            .check(classes)
    }

    /**
     * General rule: Platform layer should not access domain repositories directly
     *
     * Platform should use domain services or ACL adapters
     */
    @ArchTest
    fun `platform should not access domain repositories directly`(classes: JavaClasses) {
        noClasses()
            .that().resideInAPackage("..platform..")
            .should().dependOnClassesThat()
            .resideInAPackage("..domain.*.persistence.repository..")
            .because("ADR-018: Platform should access domain data via services, not repositories")
            .check(classes)
    }

    /**
     * Cross-domain repository access is only allowed within the same domain
     */
    @ArchTest
    fun `domains should only access their own repositories`(classes: JavaClasses) {
        // User domain can only access user repositories
        noClasses()
            .that().resideInAPackage("..domain.user..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "..domain.committee.persistence.repository..",
                "..domain.event.persistence.repository..",
                "..domain.survey.persistence.repository..",
                "..domain.contribution.persistence.repository..",
                "..domain.auth.persistence.repository.."
            )
            .because("ADR-018: Each domain should only access its own persistence layer")
            .check(classes)

        // Event domain can only access event repositories (not survey)
        noClasses()
            .that().resideInAPackage("..domain.event..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "..domain.survey.persistence.repository..",
                "..domain.user.persistence.repository..",
                "..domain.committee.persistence.repository.."
            )
            .because("ADR-018: Event domain should use services to access other domains")
            .check(classes)
    }
}
