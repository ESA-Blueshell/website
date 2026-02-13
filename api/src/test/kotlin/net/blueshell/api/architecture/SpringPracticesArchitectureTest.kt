package net.blueshell.api.architecture

import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*
import net.blueshell.api.architecture.support.ArchJUnitTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

/**
 * ArchUnit tests enforcing Spring Framework best practices.
 * Aligned with general Spring best practices and ADR-001.
 */
class SpringPracticesArchitectureTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {

    @Test
    fun `no field injection in application code`(): Unit =
        arch("No @Autowired field injection - use constructor injection") {
            noFields()
                .that().areDeclaredInClassesThat().resideInAnyPackage(
                    ArchitecturePackages.WEB,
                    ArchitecturePackages.APPLICATION,
                    ArchitecturePackages.INFRASTRUCTURE,
                    ArchitecturePackages.PLATFORM
                )
                .should().beAnnotatedWith(Autowired::class.java)
                .because("Constructor injection is preferred: immutable dependencies, simpler tests, clearer contracts")
        }

    @Test
    fun `transactional annotations in application layer only`(): Unit =
        arch("@Transactional must be in application layer (services, listeners)") {
            // Class-level @Transactional outside application layer
            noClasses()
                .that().resideOutsideOfPackages(
                    ArchitecturePackages.APPLICATION,
                    ArchitecturePackages.PLATFORM  // Jobs can be transactional
                )
                .should().beAnnotatedWith(Transactional::class.java)
                .because("ADR-001: Transaction boundaries belong in application layer")

            // Method-level @Transactional outside application layer
            noMethods()
                .that().areDeclaredInClassesThat()
                .resideOutsideOfPackages(
                    ArchitecturePackages.APPLICATION,
                    ArchitecturePackages.PLATFORM  // Jobs can be transactional
                )
                .should().beAnnotatedWith(Transactional::class.java)
                .because("ADR-001: Transaction boundaries belong in application layer")
        }

    @Test
    fun `no transactional annotations on controllers`(): Unit =
        arch("Controllers must not be @Transactional") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.WEB)
                .and().haveSimpleNameEndingWith("Controller")
                .should().beAnnotatedWith(Transactional::class.java)
                .because("ADR-002: Controllers dispatch commands; handlers manage transactions")

            noMethods()
                .that().areDeclaredInClassesThat().resideInAnyPackage(ArchitecturePackages.WEB)
                .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
                .should().beAnnotatedWith(Transactional::class.java)
                .because("ADR-002: Controllers dispatch commands; handlers manage transactions")
        }

    @Test
    fun `no transactional annotations on persistence layer`(): Unit =
        arch("Persistence layer must not be @Transactional") {
            noClasses()
                .that().resideInAnyPackage(ArchitecturePackages.PERSISTENCE)
                .should().beAnnotatedWith(Transactional::class.java)
                .because("Transaction boundaries are in application layer, not persistence")

            noMethods()
                .that().areDeclaredInClassesThat().resideInAnyPackage(ArchitecturePackages.PERSISTENCE)
                .should().beAnnotatedWith(Transactional::class.java)
                .because("Transaction boundaries are in application layer, not persistence")
        }

    @Test
    fun `services use constructor injection`(): Unit =
        arch("Services must use constructor injection") {
            noFields()
                .that().areDeclaredInClassesThat().resideInAnyPackage(ArchitecturePackages.APPLICATION)
                .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Service")
                .should().beAnnotatedWith(Autowired::class.java)
                .because("Services should use constructor injection for immutable dependencies")
        }

    @Test
    fun `command handlers use constructor injection`(): Unit =
        arch("Command handlers must use constructor injection") {
            noFields()
                .that().areDeclaredInClassesThat().resideInAnyPackage(ArchitecturePackages.COMMAND_HANDLER)
                .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Handler")
                .should().beAnnotatedWith(Autowired::class.java)
                .because("ADR-002: Handlers should use constructor injection for testability")
        }

    @Test
    fun `controllers use constructor injection`(): Unit =
        arch("Controllers must use constructor injection") {
            noFields()
                .that().areDeclaredInClassesThat().resideInAnyPackage(ArchitecturePackages.WEB)
                .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
                .should().beAnnotatedWith(Autowired::class.java)
                .because("Controllers should use constructor injection for immutable CommandBus reference")
        }
}
