package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * The registrar is what puts a record behind each definition, and what notices when a
 * definition has gone.
 */
@SpringBootTest
class CohortRegistrarIT : UserTestSupport() {
    @Autowired
    private lateinit var registrar: CohortRegistrar

    @Autowired
    private lateinit var definitions: CohortDefinitionRegistry

    @Autowired
    private lateinit var cohorts: CohortRepository

    @Autowired
    private lateinit var targets: TargetRepository

    @Test
    fun `every definition ends up with a record and a target to link`() {
        registrar.register()

        val keys = definitions.all().map { it.key }
        assertThat(keys).isNotEmpty

        keys.forEach { key ->
            val cohort = cohorts.findByDefinitionKey(key)
            assertThat(cohort).describedAs("no record for %s", key).isNotNull
            // Its target row, whose list the create-target job makes after the commit.
            val targets = targets.findAllByCohortId(cohort!!.id!!)
            assertThat(targets).describedAs("no target for %s", key).isNotEmpty
        }
    }

    @Test
    fun `running it twice creates nothing the second time`() {
        registrar.register()
        val after = registrar.register()

        assertThat(after.created).isZero()
        assertThat(after.total).isEqualTo(definitions.all().size)
    }

    @Test
    fun `a record naming no definition is reported rather than removed`() {
        val orphan =
            cohorts.save(
                Cohort(
                    type = CohortType.COMMITTEE_MEMBERS,
                    label = "Disbanded Committee",
                    definitionKey = "COMMITTEE_MEMBERS:999999",
                ),
            )

        val report = registrar.register()

        assertThat(report.orphaned).contains("COMMITTEE_MEMBERS:999999")
        // Still there: its list may be wanted, and that is not this code's call.
        assertThat(cohorts.findById(orphan.id!!)).isPresent
    }

    @Test
    fun `a cohort follows the name of the thing it is about`() {
        registrar.register()
        val definition = definitions.all().first()
        val cohort = cohorts.findByDefinitionKey(definition.key)!!

        cohort.label = "Something else entirely"
        cohorts.save(cohort)
        val report = registrar.register()

        assertThat(report.relabelled).isGreaterThanOrEqualTo(1)
        assertThat(cohorts.findByDefinitionKey(definition.key)!!.label).isEqualTo(definition.label)
    }
}
