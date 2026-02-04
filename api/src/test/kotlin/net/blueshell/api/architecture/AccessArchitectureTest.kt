package net.blueshell.api.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import net.blueshell.api.architecture.ArchitecturePackages.CONFIG
import net.blueshell.api.architecture.ArchitecturePackages.CONTROLLER
import net.blueshell.api.architecture.ArchitecturePackages.DTO
import net.blueshell.api.architecture.ArchitecturePackages.JOB
import net.blueshell.api.architecture.ArchitecturePackages.LISTENER
import net.blueshell.api.architecture.ArchitecturePackages.LISTENER_JPA
import net.blueshell.api.architecture.ArchitecturePackages.MAPPER
import net.blueshell.api.architecture.ArchitecturePackages.MODEL
import net.blueshell.api.architecture.ArchitecturePackages.REPOSITORY
import net.blueshell.api.architecture.ArchitecturePackages.SECURITY
import net.blueshell.api.architecture.ArchitecturePackages.SERVICE
import net.blueshell.api.architecture.ArchitecturePackages.VALIDATION
import net.blueshell.api.testsupport.DoNotIncludeFactory
import net.blueshell.api.testsupport.DoNotIncludeTestSupport

import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * ArchUnit: access restrictions + “best practice” boundaries between common app layers.
 */
@AnalyzeClasses(
    packages = [ArchitecturePackages.ROOT],
    importOptions = [
        ImportOption.DoNotIncludeTests::class,
        DoNotIncludeTestSupport::class,
        DoNotIncludeFactory::class
    ]
)
class AccessArchitectureTest {

    // ---- Existing rules (kept) ----

    @ArchTest
    val dtoOnlyAccessedAtApiBorder: ArchRule =
        classes()
            .that().resideInAnyPackage(DTO)
            .should().onlyBeAccessed().byAnyPackage(CONTROLLER, MAPPER, DTO, VALIDATION)
            .because("DTOs should not leak into services/repositories; keep them at the API boundary.")

    @ArchTest
    val modelsNotAccessedInDtos: ArchRule =
        noClasses()
            .that().resideInAnyPackage(DTO)
            .should().accessClassesThat().resideInAnyPackage(MODEL)
            .because("DTOs must not depend on model entities.")

    @ArchTest
    val repositoryOnlyAccessedByServicesAndValidation: ArchRule =
        classes()
            .that().resideInAnyPackage(REPOSITORY)
            .should().onlyBeAccessed().byAnyPackage(SERVICE, REPOSITORY, VALIDATION)
            .because("Repositories are an inner ring; only services/validation should touch them.")

    @ArchTest
    val jobsOnlyAccessedFromListeners: ArchRule =
        classes()
            .that().resideInAnyPackage(JOB)
            .should().onlyBeAccessed().byAnyPackage(LISTENER, JOB)
            .because("Jobs should be triggered by listeners / scheduling infrastructure.")

    @ArchTest
    val jobsNotAccessedFromJpaListeners: ArchRule =
        noClasses()
            .that().resideInAnyPackage(LISTENER_JPA)
            .should().accessClassesThat().resideInAnyPackage(JOB)
            .because("JPA listeners should not kick off jobs (transaction boundaries, side effects).")

    // ---- Stronger best-practice rules (added) ----

    @ArchTest
    val controllersDoNotAccessRepositoriesDirectly: ArchRule =
        noClasses()
            .that().resideInAnyPackage(CONTROLLER)
            .should().accessClassesThat().resideInAnyPackage(REPOSITORY)
            .because("Controllers should depend on services, never repositories directly.")

    @ArchTest
    val servicesDoNotDependOnControllers: ArchRule =
        noClasses()
            .that().resideInAnyPackage(SERVICE, VALIDATION, REPOSITORY, MODEL)
            .should().dependOnClassesThat().resideInAnyPackage(CONTROLLER)
            .because("Inner layers must not depend on the web layer.")

    @ArchTest
    val mappersAreNotUsedByRepositories: ArchRule =
        noClasses()
            .that().resideInAnyPackage(REPOSITORY)
            .should().dependOnClassesThat().resideInAnyPackage(MAPPER, DTO)
            .because("Persistence layer should not know about mapping/DTO concerns.")

    @ArchTest
    val noCyclicDependenciesByTopLevelPackage: ArchRule =
        slices()
            .matching("${ArchitecturePackages.ROOT}.(*)..")
            .should().beFreeOfCycles()
            .because("Cyclic package dependencies make refactors risky and coupling invisible.")

    @ArchTest
    val configurationDoesNotDependOnWebControllers: ArchRule =
        noClasses()
            .that().resideInAnyPackage(CONFIG, SECURITY)
            .should().dependOnClassesThat().resideInAnyPackage(CONTROLLER)
            .because("Configuration should wire beans without coupling to controllers.")
}
