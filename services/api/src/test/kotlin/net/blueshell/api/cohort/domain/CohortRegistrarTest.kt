package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CohortRegistrarTest {
    private val definitions: CohortDefinitionRegistry = mockk()
    private val cohorts: CohortRepository = mockk()
    private val targets: TargetRepository = mockk()
    private val strategies: TargetStrategies = mockk()
    private val jobs: JobQueue = mockk(relaxed = true)
    private val registrar = CohortRegistrar(definitions, cohorts, targets, strategies, jobs)

    private val definition =
        object : CohortDefinition {
            override val key = "COMMITTEE_MEMBERS:7"
            override val type = CohortType.COMMITTEE_MEMBERS
            override val scope: Long? = 7L
            override val label = "Sitecie"
            override val folder: String? = "Committees"

            override fun members(): Set<Long> = emptySet()

            override fun contains(userId: Long) = false
        }

    init {
        every { definitions.all() } returns listOf(definition)
        every { strategies.descriptor(TargetSystem.BREVO) } returns TargetDescriptor(TargetSystem.BREVO, TargetKind.LIST)
        every { cohorts.save(any()) } answers { firstArg<Cohort>().apply { id = 3L } }
        every { targets.save(any()) } answers { firstArg<Target>().apply { id = 30L } }
    }

    @Test
    fun `a new definition gets its cohort, a target row in its folder and a job that creates the list`() {
        every { cohorts.findAll() } returns emptyList()
        every { targets.findByCohortIdAndSystem(3L, "BREVO") } returns null

        val report = registrar.register()

        assertThat(report.created).isEqualTo(1)
        verify {
            targets.save(match { it.folder == "Committees" && it.label == "Sitecie" && it.cohortId == 3L })
            jobs.runAsync(CohortJobs.CreateCohortTarget, CohortJobs.CreateCohortTargetPayload(30L), JobTrigger.SITE_ACTION)
        }
    }

    @Test
    fun `a cohort that already has a target row gets no second one`() {
        every { cohorts.findAll() } returns emptyList()
        every { targets.findByCohortIdAndSystem(3L, "BREVO") } returns Entities.target(id = 30L)

        registrar.register()

        verify(exactly = 0) { targets.save(any()) }
        verify(exactly = 0) { jobs.runAsync(CohortJobs.CreateCohortTarget, any(), any()) }
    }

    @Test
    fun `a committee that renames itself renames its cohort`() {
        val renamed = Entities.cohort(id = 3L, label = "Old name").apply { definitionKey = "COMMITTEE_MEMBERS:7" }
        every { cohorts.findAll() } returns listOf(renamed)

        val report = registrar.register()

        assertThat(report.relabelled).isEqualTo(1)
        verify { cohorts.save(match { it.label == "Sitecie" }) }
    }

    @Test
    fun `a cohort that is not listed on Brevo gets no target`() {
        val activists =
            object : CohortDefinition {
                override val key = "ACTIVISTS"
                override val type = CohortType.ACTIVISTS
                override val scope: Long? = null
                override val label = "Activists"
                override val folder: String? = "Activists"

                override fun members(): Set<Long> = emptySet()

                override fun contains(userId: Long) = false
            }
        every { definitions.all() } returns listOf(activists)
        every { cohorts.findAll() } returns emptyList()

        assertThat(registrar.register().created).isEqualTo(1)
        verify(exactly = 0) { targets.save(any()) }
    }
}
