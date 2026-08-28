package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortSubject
import net.blueshell.api.cohort.persistence.CohortSubjectType
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortSubjectRepository
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
    private lateinit var subjects: CohortSubjectRepository

    @Autowired
    private lateinit var cohorts: CohortRepository

    @Test
    fun `every definition ends up with a record and a target to link`() {
        registrar.register()

        val keys = definitions.all().map { it.key }
        assertThat(keys).isNotEmpty

        keys.forEach { key ->
            val subject = subjects.findByDefinitionKey(key)
            assertThat(subject).describedAs("no record for %s", key).isNotNull
            // A target to link, with no external id until an operator supplies one.
            val targets = cohorts.findAllBySubjectId(subject!!.id!!)
            assertThat(targets).describedAs("no target for %s", key).isNotEmpty
            assertThat(targets.first().externalId).isNull()
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
        val orphan = subjects.save(
            CohortSubject(
                type = CohortSubjectType.COMMITTEE_MEMBERS,
                label = "Disbanded Committee",
                definitionKey = "COMMITTEE_MEMBERS:999999",
            ),
        )

        val report = registrar.register()

        assertThat(report.orphaned).contains("COMMITTEE_MEMBERS:999999")
        // Still there: its list may be wanted, and that is not this code's call.
        assertThat(subjects.findById(orphan.id!!)).isPresent
    }

    @Test
    fun `a cohort follows the name of the thing it is about`() {
        registrar.register()
        val definition = definitions.all().first()
        val subject = subjects.findByDefinitionKey(definition.key)!!

        subject.label = "Something else entirely"
        subjects.save(subject)
        val report = registrar.register()

        assertThat(report.relabelled).isGreaterThanOrEqualTo(1)
        assertThat(subjects.findByDefinitionKey(definition.key)!!.label).isEqualTo(definition.label)
    }
}
