package net.blueshell.api.cohort.web

import net.blueshell.api.cohort.domain.CohortDetail
import net.blueshell.api.cohort.domain.CohortQueryService
import net.blueshell.api.cohort.domain.CohortSummary
import net.blueshell.api.cohort.domain.CohortTargetRow
import net.blueshell.api.cohort.domain.CohortTargeting
import net.blueshell.api.cohort.domain.DriftResolutionRow
import net.blueshell.api.cohort.domain.DriftResolutions
import net.blueshell.api.cohort.domain.InboundReconcile
import net.blueshell.api.cohort.domain.InboundReconcileApplyRequest
import net.blueshell.api.cohort.domain.InboundReconcileApplyResponse
import net.blueshell.api.cohort.domain.InboundReconcilePreview
import net.blueshell.api.cohort.domain.LinkChoice
import net.blueshell.api.cohort.domain.LinkOutcome
import net.blueshell.api.cohort.domain.LinkProposal
import net.blueshell.api.cohort.domain.TargetCatalog
import net.blueshell.api.cohort.domain.TargetMemberRow
import net.blueshell.api.cohort.domain.TargetPlace
import net.blueshell.api.cohort.domain.TargetSummary
import net.blueshell.api.cohort.persistence.DriftResolution
import net.blueshell.api.cohort.persistence.DriftResolutionAction
import net.blueshell.api.cohort.persistence.TargetMember
import net.blueshell.api.cohort.persistence.TargetReconcileRun
import net.blueshell.api.shared.enums.TargetMemberState
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

class CohortControllerTest {
    private val queries = mock<CohortQueryService>()
    private val catalog = mock<TargetCatalog>()
    private val resolutions = mock<DriftResolutions>()
    private val targeting = mock<CohortTargeting>()
    private val inbound = mock<InboundReconcile>()
    private val controller = CohortController(queries, resolutions, targeting, inbound, catalog)
    private val json = JsonMapper.builder().findAndAddModules().build()

    @Test
    fun `a linked mapping's path is read from its system, and an unlinked one is left as it is`() {
        val linked = Entities.target(id = 1L, externalId = "10", label = "Sitecie")
        val unlinked = Entities.target(id = 2L, label = "Nintenco")
        whenever(queries.detail(7L)).thenReturn(
            CohortDetail(
                cohort = Entities.cohort(id = 7L),
                mappings =
                    listOf(
                        CohortTargetRow(linked, externalId = "10", path = listOf("Brevo")),
                        CohortTargetRow(unlinked, externalId = null, path = listOf("Brevo")),
                    ),
                members = emptyList(),
                definitionKey = "COMMITTEE_MEMBERS:7",
                orphaned = false,
            ),
        )
        whenever(catalog.placeOf(TargetSystem.BREVO, "10")).thenReturn(TargetPlace(listOf("Brevo", "Committees"), folderKnown = true))

        val detail = controller.findCohortById(7L)

        assertThat(detail.mappings.map { it.path }).containsExactly(listOf("Brevo", "Committees"), listOf("Brevo"))
        assertThat(detail.mappings.map { it.folderKnown }).containsExactly(true, true)
        verify(catalog, never()).placeOf(TargetSystem.BREVO, "")
    }

    @Test
    fun `a mapping whose folder the system cannot say says so`() {
        whenever(queries.detail(8L)).thenReturn(
            CohortDetail(
                cohort = Entities.cohort(id = 8L),
                mappings = listOf(CohortTargetRow(Entities.target(id = 3L, externalId = "11"), externalId = "11", path = listOf("Brevo"))),
                members = emptyList(),
                definitionKey = null,
                orphaned = true,
            ),
        )
        whenever(catalog.placeOf(TargetSystem.BREVO, "11")).thenReturn(TargetPlace(listOf("Brevo"), folderKnown = false))

        val mapping = controller.findCohortById(8L).mappings.single()
        assertThat(mapping.folderKnown).isFalse()
    }

    @Test
    fun `a mapping carries its recent reconciles, newest first`() {
        val run = TargetReconcileRun(3L, Instant.parse("2026-09-29T03:00:00Z"), JobTrigger.SCHEDULED_RUN, 188, 2, 1)
        whenever(queries.detail(9L)).thenReturn(
            CohortDetail(
                cohort = Entities.cohort(id = 9L),
                mappings =
                    listOf(
                        CohortTargetRow(
                            Entities.target(id = 3L).apply { enforced = true },
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
                .findCohortById(9L)
                .mappings
                .single()

        assertThat(mapping.runs).containsExactly(ReconcileRunResponse(run.startedAt, JobTrigger.SCHEDULED_RUN, 188, 2, 1))
        assertThat(mapping.enforced).isTrue()
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
        controller.reconcileTarget(1L, 2L)
        controller.evaluateUser(7L)
        verify(resolutions).reconcile(1L, 2L)
        verify(resolutions).evaluate(7L)
        verify(resolutions).enforce(1L, 2L, true)
    }

    @Test
    fun `the detail carries the recent resolutions with their names`() {
        val at = Instant.parse("2026-09-29T20:00:00Z")
        val resolution = DriftResolution(3L, DriftResolutionAction.REMOVE, null, "ext-1", "c@example.com", 4L, at)
        whenever(queries.detail(10L)).thenReturn(
            CohortDetail(
                cohort = Entities.cohort(id = 10L),
                mappings = emptyList(),
                members = emptyList(),
                definitionKey = null,
                orphaned = false,
                resolutions = listOf(DriftResolutionRow(resolution, TargetSystem.BREVO, "c@example.com", "Board Member")),
            ),
        )

        val entry = controller.findCohortById(10L).resolutions.single()

        assertThat(entry).isEqualTo(
            DriftResolutionResponse(
                targetId = 3L,
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

    @Test
    fun `the listings name each cohort and each target the pickers offer`() {
        whenever(queries.summaries()).thenReturn(listOf(CohortSummary(Entities.cohort(id = 1L, label = "Sitecie"), 4, 1)))
        whenever(queries.targets()).thenReturn(listOf(TargetSummary(Entities.target(id = 2L, label = "Sitecie"), 3)))

        val cohorts = json.writeValueAsString(controller.findCohorts())
        val targets = json.writeValueAsString(controller.listTargetOptions())

        assertThat(cohorts).contains("\"label\":\"Sitecie\"", "\"memberCount\":4", "\"mappingCount\":1", "\"category\":\"COMMITTEES\"")
        assertThat(targets).contains("\"id\":2", "\"memberCount\":3", "\"kind\":\"LIST\"")
    }

    @Test
    fun `a cohort's page carries its targets, members, runs and resolutions`() {
        val cohort = Entities.cohort(id = 11L)
        val target = Entities.target(id = 12L)
        val run = TargetReconcileRun(12L, Instant.parse("2026-09-29T03:00:00Z"), JobTrigger.SCHEDULED_RUN, 1, 2, 3)
        val member =
            TargetMember(target, 5L, cohort, externalUserId = "ext-5", label = "ada@example.com").apply {
                id = 20L
                createdAt = run.startedAt
            }
        val stranger =
            TargetMember(target, null, cohort, externalUserId = "ext-9").apply {
                id = 21L
                createdAt = run.startedAt
            }
        whenever(queries.detail(11L)).thenReturn(
            CohortDetail(
                cohort = cohort,
                mappings = listOf(CohortTargetRow(target, null, path = listOf("Brevo"), runs = listOf(run))),
                members =
                    listOf(
                        TargetMemberRow(member, Entities.user(id = 5L), system = TargetSystem.BREVO, state = TargetMemberState.DESIRED),
                        TargetMemberRow(
                            stranger,
                            null,
                            system = TargetSystem.BREVO,
                            state = TargetMemberState.STRANGER,
                            resolvedUserId = 6L,
                        ),
                    ),
                definitionKey = "COMMITTEE_MEMBERS:11",
                orphaned = false,
                resolutions =
                    listOf(
                        DriftResolutionRow(
                            DriftResolution(12L, DriftResolutionAction.PUSH, 5L, null, null, 9L, run.startedAt),
                            TargetSystem.BREVO,
                            "Ada",
                            "Board",
                        ),
                    ),
            ),
        )

        val body = json.writeValueAsString(controller.findCohortById(11L))

        assertThat(body).contains(
            "\"targetId\":12",
            "\"targetMemberId\":21",
            "\"userId\":6",
            "\"externalUserId\":\"ext-5\"",
            "\"externalLabel\":\"ada@example.com\"",
            "\"theirsOnly\":3",
            "\"personName\":\"Ada\"",
            "\"definitionKey\":\"COMMITTEE_MEMBERS:11\"",
        )
    }

    @Test
    fun `linking, creating and switching a target answer the target as it now stands`() {
        val row = CohortTargetRow(Entities.target(id = 12L, externalId = "7"), "7")
        whenever(targeting.linkExisting(1L, TargetSystem.BREVO, "7")).thenReturn(row)
        whenever(targeting.create(1L, TargetSystem.BREVO, "Members", "Lists")).thenReturn(row)
        whenever(targeting.switchTarget(1L, 12L, "7", true, true)).thenReturn(row)

        val answers =
            listOf(
                controller.linkExistingTarget(1L, LinkExistingTargetRequest(TargetSystem.BREVO, "7")),
                controller.createTarget(1L, CreateTargetRequest(TargetSystem.BREVO, "Members", "Lists")),
                controller.switchTarget(1L, 12L, SwitchTargetRequest("7", deletePrevious = true, reconcileNow = true)),
            )

        assertThat(answers.map { it.externalId }).containsOnly("7")
        assertThat(CreateTargetRequest(TargetSystem.BREVO, "Members").folderHint).isNull()
    }

    @Test
    fun `adopting reads the preview and applies the selection`() {
        val preview = InboundReconcilePreview("PERIOD_PAYERS:1", "Paid", true, "t", 0, emptyList(), emptyList())
        val applied = InboundReconcileApplyResponse(3L, 1, 0)
        val request = InboundReconcileApplyRequest("t", listOf("x"))
        whenever(inbound.preview(1L, 12L)).thenReturn(preview)
        whenever(inbound.apply(1L, 12L, request)).thenReturn(applied)

        assertThat(controller.previewInboundReconcile(1L, 12L)).isEqualTo(preview)
        assertThat(controller.applyInboundReconcile(1L, 12L, request)).isEqualTo(applied)
    }
}
