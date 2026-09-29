package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetRepository
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
    private val targets = mock<TargetRepository>()
    private val cohorts = mock<CohortRepository>()
    private val tidy: FolderTidy

    init {
        whenever(strategy.system).thenReturn(TargetSystem.BREVO)
        whenever(strategy.descriptor).thenReturn(TargetDescriptor(TargetSystem.BREVO, TargetKind.LIST))
        tidy = FolderTidy(TargetStrategies(listOf(strategy)), targets, cohorts)
    }

    private fun list(
        id: String,
        label: String,
        folder: String?,
    ) = ExternalTarget(TargetSystem.BREVO, id, TargetKind.LIST, label, folder)

    private fun given(vararg lists: ExternalTarget) {
        whenever(strategy.catalog(null)).thenReturn(lists.toList())
        whenever(strategy.folders()).thenReturn(listOf("Periods", "Committees"))
        whenever(cohorts.findAll()).thenReturn(
            listOf(
                Entities.cohort(id = 1L, type = CohortType.PERIOD_PAYERS),
                Entities.cohort(id = 2L, type = CohortType.COMMITTEE_MEMBERS),
            ),
        )
        whenever(targets.findAllBySystem("BREVO")).thenReturn(
            listOf(
                Entities.target(id = 10L, cohortId = 1L, externalId = "100"),
                Entities.target(id = 20L, cohortId = 2L, externalId = "200"),
            ),
        )
    }

    @Test
    fun `proposes each linked list out of its type's folder, and the folders to make, never an unlinked one`() {
        given(list("100", "Paid 2026", "Periods"), list("200", "Sitecie", "Committees"), list("300", "Loose", null))

        val plan = tidy.preview(TargetSystem.BREVO)

        assertThat(plan.moves).containsExactly(TidyMove("100", "Paid 2026", "Periods", "Contribution paid"))
        assertThat(plan.moves.single().from).isEqualTo("Periods")
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

        whenever(strategy.move(paid, "Contribution paid")).thenReturn(paid.copy(folderLabel = "Contribution paid"))
        assertThat(
            tidy
                .apply(TargetSystem.BREVO, listOf("100"))
                .moved
                .single()
                .folderLabel,
        ).isEqualTo("Contribution paid")
    }

    @Test
    fun `a list gone from the system fails with that reason`() {
        given(list("100", "Paid 2026", "Periods"))
        whenever(strategy.resolve("100")).thenReturn(null)

        val result = tidy.apply(TargetSystem.BREVO, listOf("100"))

        assertThat(result.failed).containsExactly(FailedTargetMove("100", "Paid 2026", "Brevo no longer has this list"))
    }

    @Test
    fun `every cohort type has its folder`() {
        assertThat(CohortType.entries.associateWith(CohortFolders::forType)).isEqualTo(
            mapOf(
                CohortType.COMMITTEE_MEMBERS to "Committees",
                CohortType.PERIOD_PAYERS to "Contribution paid",
                CohortType.PERIOD_MEMBERS to "Members",
                CohortType.PERIOD_ACTIVE_MEMBERS to "Active members",
                CohortType.NEWSLETTER_SUBSCRIBERS to "Newsletter",
            ),
        )
    }
}
