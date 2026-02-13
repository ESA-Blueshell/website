package net.blueshell.api.architecture

import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*
import net.blueshell.api.architecture.support.ArchJUnitTestBase
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

class SpringPracticesArchitectureTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {

    @Test
    fun `no field injection in application code`(): Unit =
        arch("No @Autowired field injection") {
            noFields()
                .that().areDeclaredInClassesThat().resideInAnyPackage(
                    ArchitecturePackages.CONTROLLER,
                    ArchitecturePackages.SERVICE,
                    ArchitecturePackages.VALIDATION,
                    ArchitecturePackages.JOB,
                    ArchitecturePackages.LISTENER
                )
                .should().beAnnotatedWith(Autowired::class.java)
                .because("Prefer constructor injection (immutable dependencies, simpler tests, fewer Spring edge cases).")
        }

    @Test
    fun `transactional annotations should be in service or job only (class or method)`(): Unit =
        arch("@Transactional stays in services/jobs") {
            // class-level @Transactional outside allowed packages
            noClasses()
                .that().resideOutsideOfPackages(ArchitecturePackages.SERVICE, ArchitecturePackages.JOB)
                .should().beAnnotatedWith(Transactional::class.java)
                .because("Keep transaction boundaries in service/job layer.")

            // method-level @Transactional outside allowed packages
            noMethods()
                .that().areDeclaredInClassesThat()
                .resideOutsideOfPackages(ArchitecturePackages.SERVICE, ArchitecturePackages.JOB)
                .should().beAnnotatedWith(Transactional::class.java)
                .because("Keep transaction boundaries in service/job layer.")
        }
}
