package net.blueshell.api.cohort.web

import net.blueshell.api.cohort.domain.CohortDetail
import net.blueshell.api.cohort.domain.CohortQueryService
import net.blueshell.api.cohort.domain.CohortRemediation
import net.blueshell.api.cohort.domain.CohortSummary
import net.blueshell.api.cohort.domain.CohortTargetRow
import net.blueshell.api.cohort.domain.CohortTargeting
import net.blueshell.api.cohort.domain.InboundReconcile
import net.blueshell.api.cohort.domain.InboundReconcileApplyRequest
import net.blueshell.api.cohort.domain.InboundReconcileApplyResponse
import net.blueshell.api.cohort.domain.InboundReconcilePreview
import net.blueshell.api.cohort.domain.SummaryTarget
import net.blueshell.api.cohort.domain.TargetCatalog
import net.blueshell.api.cohort.domain.TargetMemberRow
import net.blueshell.api.cohort.domain.TargetPlace
import net.blueshell.api.cohort.domain.TargetSummary
import net.blueshell.api.cohort.persistence.TargetMember
import net.blueshell.api.shared.enums.TargetMemberState
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.sync.persistence.ExternalIdMapping
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

class CohortControllerTest {
    private val queries = mock<CohortQueryService>()
    private val remediation = mock<CohortRemediation>()
    private val targeting = mock<CohortTargeting>()
    private val inbound = mock<InboundReconcile>()
    private val catalog = mock<TargetCatalog>()
    private val controller = CohortController(queries, remediation, targeting, inbound, catalog)
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
        verify(catalog, times(1)).placeOf(any(), any())
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
    fun `the listings name each cohort and each target the pickers offer`() {
        whenever(
            queries.summaries(),
        ).thenReturn(
            listOf(
                CohortSummary(
                    Entities.cohort(id = 1L, label = "Sitecie"),
                    4,
                    1,
                    listOf(SummaryTarget(TargetSystem.BREVO, "Sitecie", true)),
                ),
            ),
        )
        whenever(queries.targets()).thenReturn(listOf(TargetSummary(Entities.target(id = 2L, label = "Sitecie"), 3)))

        val cohorts = json.writeValueAsString(controller.findCohorts())
        val targets = json.writeValueAsString(controller.listTargetOptions())

        assertThat(cohorts).contains(
            "\"id\":1",
            "\"label\":\"Sitecie\"",
            "\"memberCount\":4",
            "\"mappingCount\":1",
            "\"category\":\"COMMITTEES\"",
        )
        assertThat(targets).contains("\"id\":2", "\"system\":\"BREVO\"", "\"memberCount\":3", "\"kind\":\"LIST\"")
    }

    @Test
    fun `a cohort's page carries its targets and its members, a stranger under the account its external id resolves to`() {
        val cohort = Entities.cohort(id = 11L)
        val target = Entities.target(id = 12L)
        val joined = Instant.parse("2026-09-29T03:00:00Z")
        val member =
            TargetMember(target, 5L, cohort, externalUserId = "ext-5", label = "ada@example.com").apply {
                id = 20L
                createdAt = joined
            }
        val stranger =
            TargetMember(target, null, cohort, externalUserId = "ext-9").apply {
                id = 21L
                createdAt = joined
            }
        whenever(queries.detail(11L)).thenReturn(
            CohortDetail(
                cohort = cohort,
                mappings = listOf(CohortTargetRow(target, "7", lastReconciledAt = joined, path = listOf("Brevo", "Committees"))),
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
            ),
        )
        whenever(catalog.placeOf(TargetSystem.BREVO, "7")).thenReturn(TargetPlace(listOf("Brevo", "Committees"), folderKnown = true))

        val page = controller.findCohortById(11L)

        assertThat(page.mappings.single())
            .extracting({ it.targetId }, { it.system }, { it.externalId }, { it.path })
            .containsExactly(12L, TargetSystem.BREVO, "7", listOf("Brevo", "Committees"))
        assertThat(page.members.map { it.targetMemberId to it.userId }).containsExactly(20L to 5L, 21L to 6L)
        assertThat(page.members.first())
            .extracting({ it.externalUserId }, { it.externalLabel }, { it.userEmail }, { it.state }, { it.joinedAt })
            .containsExactly("ext-5", "ada@example.com", "user5@example.com", TargetMemberState.DESIRED, joined)
        assertThat(json.writeValueAsString(page)).contains(
            "\"definitionKey\":\"COMMITTEE_MEMBERS:11\"",
            "\"category\":\"COMMITTEES\"",
            "\"orphaned\":false",
        )
    }

    @Test
    fun `linking a user answers the account and the id it now has on the system`() {
        whenever(remediation.linkUser(1L, 7L, TargetSystem.BREVO, "ext-7"))
            .thenReturn(ExternalIdMapping("USER", 7L, TargetSystem.BREVO.name, "ext-7"))

        val linked = controller.linkUser(1L, LinkUserRequest(7L, TargetSystem.BREVO, "ext-7"))

        assertThat(linked).isEqualTo(LinkedUserResponse(7L, TargetSystem.BREVO, "ext-7"))
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

        assertThat(answers.map { it.targetId to it.externalId }).containsOnly(12L to "7")
        assertThat(CreateTargetRequest(TargetSystem.BREVO, "Members").folderHint).isNull()
        assertThat(SwitchTargetRequest("7")).extracting({ it.deletePrevious }, { it.reconcileNow }).containsExactly(false, false)
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
