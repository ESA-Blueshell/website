package net.blueshell.api.platform.integration.cohort.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortFactKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubject
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectType
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CohortProvisioningServiceTest {

    private val subjects: CohortSubjectRepository = mockk(relaxed = true)
    private val cohorts: CohortRepository = mockk(relaxed = true)
    private val service = CohortProvisioningService(subjects, cohorts)

    private fun spec(system: TargetSystem = TargetSystem.BREVO) = CohortProvisioningSpec(
        factKind = CohortFactKind.COMMITTEE,
        factKey = "7",
        subjectType = CohortSubjectType.COMMITTEE_MEMBERS,
        system = system,
        label = "Web Cmte",
        folder = "Committees",
    )

    private fun subject(id: Long, enabled: Boolean = true) =
        CohortSubject(
            type = CohortSubjectType.COMMITTEE_MEMBERS,
            label = "Web Cmte",
            factKind = CohortFactKind.COMMITTEE,
            factKey = "7",
            enabled = enabled,
        ).apply { this.id = id }

    private fun cohort(id: Long, subjectId: Long, system: String = "BREVO") =
        Cohort(system = system, kind = CohortKind.LIST, label = "Web Cmte", subjectId = subjectId).apply { this.id = id }

    @Test
    fun `creates the subject and cohort when neither exists`() {
        every { subjects.findByFactKindAndFactKey(CohortFactKind.COMMITTEE, "7") } returns null
        val savedSubject = subject(id = 5L)
        every { subjects.save(any()) } returns savedSubject
        every { cohorts.findBySubjectIdAndSystem(5L, "BREVO") } returns null
        val savedCohort = cohort(id = 50L, subjectId = 5L)
        every { cohorts.save(any()) } returns savedCohort

        val result = service.provision(spec())

        assertThat(result).isEqualTo(CohortProvisioningResult.Ready(savedCohort))
        val subjectSlot = slot<CohortSubject>()
        verify { subjects.save(capture(subjectSlot)) }
        assertThat(subjectSlot.captured.factKind).isEqualTo(CohortFactKind.COMMITTEE)
        assertThat(subjectSlot.captured.factKey).isEqualTo("7")
        assertThat(subjectSlot.captured.enabled).isTrue()
    }

    @Test
    fun `reuses an existing enabled subject and only adds the cohort`() {
        every { subjects.findByFactKindAndFactKey(CohortFactKind.COMMITTEE, "7") } returns subject(id = 5L)
        every { cohorts.findBySubjectIdAndSystem(5L, "BREVO") } returns null
        every { cohorts.save(any()) } returns cohort(id = 50L, subjectId = 5L)

        service.provision(spec())

        verify(exactly = 0) { subjects.save(any()) }
        verify { cohorts.save(any()) }
    }

    @Test
    fun `is idempotent when subject and cohort already exist`() {
        val existingCohort = cohort(id = 50L, subjectId = 5L)
        every { subjects.findByFactKindAndFactKey(CohortFactKind.COMMITTEE, "7") } returns subject(id = 5L)
        every { cohorts.findBySubjectIdAndSystem(5L, "BREVO") } returns existingCohort

        val result = service.provision(spec())

        assertThat(result).isEqualTo(CohortProvisioningResult.Ready(existingCohort))
        verify(exactly = 0) { subjects.save(any()) }
        verify(exactly = 0) { cohorts.save(any()) }
    }

    @Test
    fun `a disabled subject is not bypassed`() {
        every { subjects.findByFactKindAndFactKey(CohortFactKind.COMMITTEE, "7") } returns subject(id = 5L, enabled = false)

        val result = service.provision(spec())

        assertThat(result).isEqualTo(CohortProvisioningResult.Disabled(5L))
        verify(exactly = 0) { subjects.save(any()) }
        verify(exactly = 0) { cohorts.save(any()) }
    }

    @Test
    fun `a second system reuses the subject rather than creating a new one`() {
        val googleCohort = cohort(id = 60L, subjectId = 5L, system = "GOOGLE_CALENDAR")
        every { subjects.findByFactKindAndFactKey(CohortFactKind.COMMITTEE, "7") } returns subject(id = 5L)
        every { cohorts.findBySubjectIdAndSystem(5L, "GOOGLE_CALENDAR") } returns googleCohort

        val result = service.provision(spec(system = TargetSystem.GOOGLE_CALENDAR))

        assertThat(result).isEqualTo(CohortProvisioningResult.Ready(googleCohort))
        verify(exactly = 0) { subjects.save(any()) }
    }
}
