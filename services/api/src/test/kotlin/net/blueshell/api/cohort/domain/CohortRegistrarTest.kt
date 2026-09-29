package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortKind
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortSubject
import net.blueshell.api.cohort.persistence.CohortSubjectRepository
import net.blueshell.api.cohort.persistence.CohortSubjectType
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CohortRegistrarTest {
    private val definitions: CohortDefinitionRegistry = mockk()
    private val subjects: CohortSubjectRepository = mockk()
    private val cohorts: CohortRepository = mockk()
    private val strategies: TargetStrategies = mockk()
    private val jobs: JobQueue = mockk(relaxed = true)
    private val registrar = CohortRegistrar(definitions, subjects, cohorts, strategies, jobs)

    private val definition =
        object : CohortDefinition {
            override val key = "COMMITTEE_MEMBERS:7"
            override val type = CohortSubjectType.COMMITTEE_MEMBERS
            override val scope: Long? = 7L
            override val label = "Sitecie"
            override val folder: String? = "Committees"

            override fun members(): Set<Long> = emptySet()

            override fun contains(userId: Long) = false
        }

    init {
        every { definitions.all() } returns listOf(definition)
        every { strategies.descriptor(TargetSystem.BREVO) } returns TargetDescriptor(TargetSystem.BREVO, CohortKind.LIST)
        every { subjects.save(any()) } answers { firstArg<CohortSubject>().apply { id = 3L } }
        every { cohorts.save(any()) } answers { firstArg<Cohort>().apply { id = 30L } }
    }

    @Test
    fun `a new definition gets its cohort, a target row in its folder and a job that creates the list`() {
        every { subjects.findAll() } returns emptyList()
        every { cohorts.findBySubjectIdAndSystem(3L, "BREVO") } returns null

        val report = registrar.register()

        assertThat(report.created).isEqualTo(1)
        verify {
            cohorts.save(match { it.folder == "Committees" && it.label == "Sitecie" && it.subjectId == 3L })
            jobs.runAsync(CohortJobs.CreateCohortTarget, CohortJobs.CreateCohortTargetPayload(30L), JobTrigger.SITE_ACTION)
        }
    }

    @Test
    fun `a subject that already has a target row gets no second one`() {
        every { subjects.findAll() } returns emptyList()
        every { cohorts.findBySubjectIdAndSystem(3L, "BREVO") } returns Entities.cohort(id = 30L)

        registrar.register()

        verify(exactly = 0) { cohorts.save(any()) }
        verify(exactly = 0) { jobs.runAsync(CohortJobs.CreateCohortTarget, any(), any()) }
    }
}
