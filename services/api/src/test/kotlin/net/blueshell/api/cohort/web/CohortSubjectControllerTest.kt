package net.blueshell.api.cohort.web

import net.blueshell.api.cohort.domain.CohortMappingRow
import net.blueshell.api.cohort.domain.CohortSubjectDetail
import net.blueshell.api.cohort.domain.CohortSubjectQueryService
import net.blueshell.api.cohort.domain.TargetCatalog
import net.blueshell.api.cohort.domain.TargetPlace
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CohortSubjectControllerTest {
    private val queries = mock<CohortSubjectQueryService>()
    private val catalog = mock<TargetCatalog>()
    private val controller = CohortSubjectController(queries, mock(), mock(), mock(), catalog)

    @Test
    fun `a linked mapping's path is read from its system, and an unlinked one is left as it is`() {
        val linked = Entities.cohort(id = 1L, externalId = "10", label = "Sitecie")
        val unlinked = Entities.cohort(id = 2L, label = "Nintenco")
        whenever(queries.detail(7L)).thenReturn(
            CohortSubjectDetail(
                subject = Entities.cohortSubject(id = 7L),
                mappings =
                    listOf(
                        CohortMappingRow(linked, externalId = "10", path = listOf("Brevo")),
                        CohortMappingRow(unlinked, externalId = null, path = listOf("Brevo")),
                    ),
                members = emptyList(),
                definitionKey = "COMMITTEE_MEMBERS:7",
                orphaned = false,
            ),
        )
        whenever(catalog.placeOf(TargetSystem.BREVO, "10")).thenReturn(TargetPlace(listOf("Brevo", "Committees"), folderKnown = true))

        val detail = controller.findCohortSubjectById(7L)

        assertThat(detail.mappings.map { it.path }).containsExactly(listOf("Brevo", "Committees"), listOf("Brevo"))
        assertThat(detail.mappings.map { it.folderKnown }).containsExactly(true, true)
        verify(catalog, never()).placeOf(TargetSystem.BREVO, "")
    }

    @Test
    fun `a mapping whose folder the system cannot say says so`() {
        whenever(queries.detail(8L)).thenReturn(
            CohortSubjectDetail(
                subject = Entities.cohortSubject(id = 8L),
                mappings = listOf(CohortMappingRow(Entities.cohort(id = 3L, externalId = "11"), externalId = "11", path = listOf("Brevo"))),
                members = emptyList(),
                definitionKey = null,
                orphaned = true,
            ),
        )
        whenever(catalog.placeOf(TargetSystem.BREVO, "11")).thenReturn(TargetPlace(listOf("Brevo"), folderKnown = false))

        val mapping = controller.findCohortSubjectById(8L).mappings.single()
        assertThat(mapping.folderKnown).isFalse()
    }
}
