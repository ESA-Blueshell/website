package net.blueshell.api.cohort.web

import net.blueshell.api.cohort.domain.CohortMappingRow
import net.blueshell.api.cohort.domain.CohortSubjectDetail
import net.blueshell.api.cohort.domain.CohortSubjectQueryService
import net.blueshell.api.cohort.domain.DriftResolutionRow
import net.blueshell.api.cohort.domain.DriftResolutions
import net.blueshell.api.cohort.domain.LinkChoice
import net.blueshell.api.cohort.domain.LinkOutcome
import net.blueshell.api.cohort.domain.LinkProposal
import net.blueshell.api.cohort.domain.TargetCatalog
import net.blueshell.api.cohort.domain.TargetPlace
import net.blueshell.api.cohort.persistence.DriftResolution
import net.blueshell.api.cohort.persistence.DriftResolutionAction
import net.blueshell.api.cohort.persistence.TargetReconcileRun
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class CohortSubjectControllerTest {
    private val queries = mock<CohortSubjectQueryService>()
    private val catalog = mock<TargetCatalog>()
    private val resolutions = mock<DriftResolutions>()
    private val controller = CohortSubjectController(queries, resolutions, mock(), mock(), catalog)

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

    @Test
    fun `a mapping carries its recent reconciles, newest first`() {
        val run = TargetReconcileRun(3L, Instant.parse("2026-09-29T03:00:00Z"), JobTrigger.SCHEDULED_RUN, 188, 2, 1)
        whenever(queries.detail(9L)).thenReturn(
            CohortSubjectDetail(
                subject = Entities.cohortSubject(id = 9L),
                mappings =
                    listOf(
                        CohortMappingRow(
                            Entities.cohort(id = 3L).apply { enforced = true },
                            null,
                            path = listOf("Brevo"),
                            runs = listOf(run),
                        ),
                    ),
                members = emptyList(),
                definitionKey = null,
                orphaned = false,
            ),
        )

        val mapping =
            controller
                .findCohortSubjectById(9L)
                .mappings
                .single()

        assertThat(mapping.runs).containsExactly(ReconcileRunResponse(run.startedAt, JobTrigger.SCHEDULED_RUN, 188, 2, 1))
        assertThat(mapping.enforced).isTrue()
        assertThat(mapping.runs.single().trigger).isEqualTo(JobTrigger.SCHEDULED_RUN)
    }

    @Test
    fun `the drift actions hand their people to the resolutions`() {
        whenever(resolutions.push(1L, 2L, listOf(5L))).thenReturn(1)
        whenever(resolutions.remove(1L, 2L, listOf("x"))).thenReturn(1)
        val proposal = LinkProposal("x", "a@example.com", 5L, "Ada")
        whenever(resolutions.proposeLinks(1L, 2L, listOf("x"))).thenReturn(listOf(proposal))
        val outcome = LinkOutcome(1, emptyList())
        whenever(resolutions.link(1L, 2L, listOf(LinkChoice("x", 5L)))).thenReturn(outcome)

        assertThat(controller.pushDrift(1L, 2L, PushDriftRequest(listOf(5L))).resolved).isEqualTo(1)
        assertThat(controller.removeDrift(1L, 2L, ExternalDriftRequest(listOf("x"))).resolved).isEqualTo(1)
        assertThat(controller.proposeLinks(1L, 2L, ExternalDriftRequest(listOf("x")))).containsExactly(proposal)
        assertThat(controller.linkDrift(1L, 2L, LinkDriftRequest(listOf(LinkChoice("x", 5L))))).isEqualTo(outcome)
        controller.enforceTarget(1L, 2L, EnforceTargetRequest(true))
        verify(resolutions).enforce(1L, 2L, true)
    }

    @Test
    fun `the detail carries the recent resolutions with their names`() {
        val at = Instant.parse("2026-09-29T20:00:00Z")
        val resolution = DriftResolution(3L, DriftResolutionAction.REMOVE, null, "ext-1", "c@example.com", 4L, at)
        whenever(queries.detail(10L)).thenReturn(
            CohortSubjectDetail(
                subject = Entities.cohortSubject(id = 10L),
                mappings = emptyList(),
                members = emptyList(),
                definitionKey = null,
                orphaned = false,
                resolutions = listOf(DriftResolutionRow(resolution, TargetSystem.BREVO, "c@example.com", "Board Member")),
            ),
        )

        val entry = controller.findCohortSubjectById(10L).resolutions.single()

        assertThat(listOf(entry.personName, entry.resolvedByName)).containsExactly("c@example.com", "Board Member")

        assertThat(entry).isEqualTo(
            DriftResolutionResponse(
                cohortId = 3L,
                system = TargetSystem.BREVO,
                action = DriftResolutionAction.REMOVE,
                userId = null,
                externalUserId = "ext-1",
                personName = "c@example.com",
                resolvedByName = "Board Member",
                resolvedAt = at,
            ),
        )
    }
}
