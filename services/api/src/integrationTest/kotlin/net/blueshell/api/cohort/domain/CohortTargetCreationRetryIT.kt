package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.contact.domain.MockContactAdapter
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doCallRealMethod
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean

/** A create-target job that fails after Brevo made the list is retried without a second list (api ADR-035). */
@SpringBootTest
class CohortTargetCreationRetryIT : UserTestSupport() {
    @Autowired
    private lateinit var handlers: CohortJobHandlers

    @Autowired
    private lateinit var cohorts: CohortRepository

    @Autowired
    private lateinit var targets: TargetRepository

    @Autowired
    private lateinit var brevo: MockContactAdapter

    @MockitoSpyBean
    private lateinit var targetIds: CohortTargetIds

    @Test
    fun `a run that made the list and failed before linking it is retried without making a second list`() {
        val cohort = cohorts.save(Cohort(type = CohortType.COMMITTEE_MEMBERS, label = "Retrycie", definitionKey = "COMMITTEE_MEMBERS:990"))
        val target =
            targets.save(
                Target(
                    system = TargetSystem.BREVO.name,
                    kind = TargetKind.LIST,
                    label = "Retrycie",
                    folder = "Committees",
                    cohortId = cohort.id,
                ),
            )
        val job = handlers.createCohortTargetHandler()
        val payload = """{"cohortId":${target.id}}"""
        doThrow(IllegalStateException("the database went away")).doCallRealMethod().whenever(targetIds).record(any(), any())

        assertThrows<IllegalStateException> { job.handle(payload, null, false) }
        job.handle(payload, null, false)

        val made = brevo.getAllLists().values.filter { it.listName == "Retrycie" }
        assertThat(made).hasSize(1)
        assertThat(targets.findById(target.id!!).orElseThrow().externalId).isEqualTo(made.single().listId.toString())
    }
}
