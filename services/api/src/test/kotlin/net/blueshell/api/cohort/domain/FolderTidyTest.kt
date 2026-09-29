package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortKind
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortSubjectRepository
import net.blueshell.api.cohort.persistence.CohortSubjectType
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FolderTidyTest {
    private val strategy = mock<TargetStrategy>()
    private val cohorts = mock<CohortRepository>()
    private val subjects = mock<CohortSubjectRepository>()
    private val tidy: FolderTidy

    init {
        whenever(strategy.system).thenReturn(TargetSystem.BREVO)
        whenever(strategy.descriptor).thenReturn(TargetDescriptor(TargetSystem.BREVO, CohortKind.LIST))
        tidy = FolderTidy(TargetStrategies(listOf(strategy)), cohorts, subjects)
    }

    private fun list(
        id: String,
        label: String,
        folder: String?,
    ) = ExternalTarget(TargetSystem.BREVO, id, CohortKind.LIST, label, folder)

    private fun given(vararg lists: ExternalTarget) {
        whenever(strategy.catalog(null)).thenReturn(lists.toList())
        whenever(strategy.folders()).thenReturn(listOf("Periods", "Committees"))
        whenever(subjects.findAll()).thenReturn(
            listOf(
                Entities.cohortSubject(id = 1L, type = CohortSubjectType.PERIOD_PAYERS),
                Entities.cohortSubject(id = 2L, type = CohortSubjectType.COMMITTEE_MEMBERS),
            ),
        )
        whenever(cohorts.findAllBySystem("BREVO")).thenReturn(
            listOf(
                Entities.cohort(id = 10L, subjectId = 1L, externalId = "100"),
                Entities.cohort(id = 20L, subjectId = 2L, externalId = "200"),
            ),
        )
    }

    @Test
    fun `proposes each linked list out of its type's folder, and the folders to make, never an unlinked one`() {
        given(list("100", "Paid 2026", "Periods"), list("200", "Sitecie", "Committees"), list("300", "Loose", null))

        val plan = tidy.preview(TargetSystem.BREVO)

        assertThat(plan.moves).containsExactly(TidyMove("100", "Paid 2026", "Periods", "Contribution paid"))
        assertThat(plan.foldersToCreate).containsExactly("Contribution paid")
    }

    @Test
    fun `a tidy already applied proposes nothing`() {
        given(list("100", "Paid 2026", "Contribution paid"), list("200", "Sitecie", "Committees"))

        assertThat(tidy.preview(TargetSystem.BREVO).moves).isEmpty()
    }

    @Test
    fun `applying moves only the lists picked, and keeps each failure's reason`() {
        given(list("100", "Paid 2026", "Periods"), list("200", "Sitecie", null))
        val paid = list("100", "Paid 2026", "Periods")
        val sitecie = list("200", "Sitecie", null)
        whenever(strategy.resolve("100")).thenReturn(paid)
        whenever(strategy.resolve("200")).thenReturn(sitecie)
        whenever(strategy.move(sitecie, "Committees")).thenThrow(IllegalStateException("Brevo said no"))

        val chosenNone = tidy.apply(TargetSystem.BREVO, emptyList())
        assertThat(chosenNone.moved).isEmpty()
        verify(strategy, never()).move(any(), any())

        val result = tidy.apply(TargetSystem.BREVO, listOf("200"))

        verify(strategy, never()).move(paid, "Contribution paid")
        assertThat(result.failed).containsExactly(FailedTargetMove("200", "Sitecie", "Brevo said no"))
    }

    @Test
    fun `every cohort type has its folder`() {
        assertThat(CohortSubjectType.entries.associateWith(CohortFolders::forType)).isEqualTo(
            mapOf(
                CohortSubjectType.COMMITTEE_MEMBERS to "Committees",
                CohortSubjectType.PERIOD_PAYERS to "Contribution paid",
                CohortSubjectType.PERIOD_MEMBERS to "Members",
                CohortSubjectType.PERIOD_ACTIVE_MEMBERS to "Active members",
                CohortSubjectType.NEWSLETTER_SUBSCRIBERS to "Newsletter",
            ),
        )
    }
}
