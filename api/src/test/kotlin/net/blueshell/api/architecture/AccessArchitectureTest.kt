package net.blueshell.api.architecture

import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import net.blueshell.api.architecture.ArchitecturePackages.CONFIG
import net.blueshell.api.architecture.ArchitecturePackages.CONTROLLER
import net.blueshell.api.architecture.ArchitecturePackages.DTO
import net.blueshell.api.architecture.ArchitecturePackages.JOB
import net.blueshell.api.architecture.ArchitecturePackages.LISTENER
import net.blueshell.api.architecture.ArchitecturePackages.LISTENER_JPA
import net.blueshell.api.architecture.ArchitecturePackages.MODEL
import net.blueshell.api.architecture.ArchitecturePackages.REPOSITORY
import net.blueshell.api.architecture.ArchitecturePackages.SECURITY
import net.blueshell.api.architecture.ArchitecturePackages.SERVICE
import net.blueshell.api.architecture.ArchitecturePackages.VALIDATION
import net.blueshell.api.architecture.support.ArchJUnitTestBase
import org.junit.jupiter.api.Test

class AccessArchitectureTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {

    @Test
    fun `dto only accessed at api border`(): Unit =
        arch("DTOs only accessed at API border") {
            classes()
                .that().resideInAnyPackage(DTO)
                .should().onlyBeAccessed().byAnyPackage(CONTROLLER, DTO, MODEL, VALIDATION)
                .because("DTOs should not leak into services/repositories; keep them at the API boundary.")
        }

    @Test
    fun `repository only accessed by services and validation`(): Unit =
        arch("Repositories only accessed from service/validation") {
            classes()
                .that().resideInAnyPackage(REPOSITORY)
                .should().onlyBeAccessed().byAnyPackage(SERVICE, REPOSITORY, VALIDATION)
                .because("Repositories are an inner ring; only services/validation should touch them.")
        }

    @Test
    fun `jobs only accessed from listeners`(): Unit =
        arch("Jobs only triggered by listeners") {
            classes()
                .that().resideInAnyPackage(JOB)
                .should().onlyBeAccessed().byAnyPackage(LISTENER, JOB)
                .because("Jobs should be triggered by listeners / scheduling infrastructure.")
        }

    @Test
    fun `jobs not accessed from jpa listeners`(): Unit =
        arch("No job triggers from JPA listeners") {
            noClasses()
                .that().resideInAnyPackage(LISTENER_JPA)
                .should().accessClassesThat().resideInAnyPackage(JOB)
                .because("JPA listeners should not kick off jobs (transaction boundaries, side effects).")
        }

    @Test
    fun `controllers do not access repositories directly`(): Unit =
        arch("Controllers must not access repositories") {
            noClasses()
                .that().resideInAnyPackage(CONTROLLER)
                .should().accessClassesThat().resideInAnyPackage(REPOSITORY)
                .because("Controllers should depend on services, never repositories directly.")
        }

    @Test
    fun `services do not depend on controllers`(): Unit =
        arch("Inner layers must not depend on controllers") {
            noClasses()
                .that().resideInAnyPackage(SERVICE, REPOSITORY, MODEL)
                .should().dependOnClassesThat().resideInAnyPackage(CONTROLLER)
                .because("Inner layers must not depend on the web layer.")
        }

    @Test
    fun `services do not depend on DTOs`(): Unit =
        arch("Services must not depend on DTOs") {
            noClasses()
                .that().resideInAnyPackage(SERVICE)
                .should().dependOnClassesThat().resideInAnyPackage(DTO)
                .because("Keep DTO usage at the boundary; services should work with domain types/commands.")
        }

    @Test
    fun `mappers are not used by repositories`(): Unit =
        arch("Repositories must not depend on mappers/DTO") {
            noClasses()
                .that().resideInAnyPackage(REPOSITORY)
                .should().dependOnClassesThat().resideInAnyPackage(DTO)
                .because("Persistence layer should not know about mapping/DTO concerns.")
        }

    @Test
    fun `repositories do not depend on services`(): Unit =
        arch("Repositories must not depend on services") {
            noClasses()
                .that().resideInAnyPackage(REPOSITORY)
                .should().dependOnClassesThat().resideInAnyPackage(SERVICE)
                .because("Dependency direction should be Service -> Repository, never the other way around.")
        }

    @Test
    fun `no cyclic dependencies by top level package`(): Unit =
        arch("No cyclic dependencies by top-level package") {
            slices()
                .matching("${ArchitecturePackages.ROOT}.(*)..")
                .should().beFreeOfCycles()
                .because("Cyclic package dependencies make refactors risky and coupling invisible.")
        }

    @Test
    fun `configuration does not depend on web controllers`(): Unit =
        arch("Config must not depend on controllers") {
            noClasses()
                .that().resideInAnyPackage(CONFIG, SECURITY)
                .should().dependOnClassesThat().resideInAnyPackage(CONTROLLER)
                .because("Configuration should wire beans without coupling to controllers.")
        }
}
