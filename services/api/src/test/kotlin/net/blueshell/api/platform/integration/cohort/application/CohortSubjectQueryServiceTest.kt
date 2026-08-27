package net.blueshell.api.platform.integration.cohort.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.cohort.application.definition.CohortDefinitionRegistry
import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortMember
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubject
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectType
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
import net.blueshell.api.platform.integration.cohort.port.out.TargetDescriptor
import net.blueshell.api.platform.integration.cohort.port.out.TargetStrategy
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
import net.blueshell.api.shared.enums.CohortMemberState
import net.blueshell.api.shared.enums.TargetSystem
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CohortSubjectQueryServiceTest {

    private val subjects: CohortSubjectRepository = mockk()
    private val cohorts: CohortRepository = mockk()
    private val cohortMembers: CohortMemberRepository = mockk()
    private val users: UserService = mockk()
    private val targetIds: CohortTargetIds = mockk()
    private val externalIds: ExternalIdMappingService = mockk()
    private val definitions: CohortDefinitionRegistry = mockk()
    private val brevo: TargetStrategy = mockk<TargetStrategy>().also {
        every { it.system } returns TargetSystem.BREVO
        every { it.descriptor } returns TargetDescriptor(
            system = TargetSystem.BREVO,
            kind = CohortKind.LIST,
            systemLabel = "Brevo",
            targetLabel = "List",
            idLabel = "List id",
            folderLabel = "Folder",
            capabilities = emptySet(),
        )
    }
    private val strategies: TargetStrategies = TargetStrategies(listOf(brevo))
    private val service = CohortSubjectQueryService(
        subjects,
        cohorts,
        cohortMembers,
        users,
        targetIds,
        externalIds,
        definitions,
        strategies,
    )

    @Test
    fun `summaries returns memberCount and mappingCount from count methods not findAll`() {
        val subject = subject(1L)
        every { subjects.findAll() } returns listOf(subject)
        every { cohortMembers.countBySubjectIdAndUserIdIsNotNull(1L) } returns 4L
        every { cohorts.countBySubjectId(1L) } returns 2L

        val result = service.summaries()

        assertThat(result).hasSize(1)
        assertThat(result[0].memberCount).isEqualTo(4)
        assertThat(result[0].mappingCount).isEqualTo(2)
        verify(exactly = 1) { cohortMembers.countBySubjectIdAndUserIdIsNotNull(1L) }
        verify(exactly = 1) { cohorts.countBySubjectId(1L) }
        verify(exactly = 0) { cohortMembers.findAllBySubjectIdAndUserIdIsNotNull(any()) }
        verify(exactly = 0) { cohorts.findAllBySubjectId(any()) }
    }

    @Test
    fun `summaries excludes stranger rows (userId == null) from memberCount`() {
        val subject = subject(2L)
        every { subjects.findAll() } returns listOf(subject)
        // 1 desired member; stranger rows (null userId) are excluded by the count predicate
        every { cohortMembers.countBySubjectIdAndUserIdIsNotNull(2L) } returns 1L
        every { cohorts.countBySubjectId(2L) } returns 3L

        val result = service.summaries()

        assertThat(result[0].memberCount).isEqualTo(1)
        assertThat(result[0].mappingCount).isEqualTo(3)
    }

    @Test
    fun `summaries returns empty list when no subjects exist`() {
        every { subjects.findAll() } returns emptyList()

        val result = service.summaries()

        assertThat(result).isEmpty()
        verify(exactly = 0) { cohortMembers.countBySubjectIdAndUserIdIsNotNull(any()) }
        verify(exactly = 0) { cohorts.countBySubjectId(any()) }
    }

    @Test
    fun `summaries aggregates counts across multiple subjects`() {
        val subjectA = subject(10L)
        val subjectB = subject(11L)
        every { subjects.findAll() } returns listOf(subjectA, subjectB)
        every { cohortMembers.countBySubjectIdAndUserIdIsNotNull(10L) } returns 7L
        every { cohortMembers.countBySubjectIdAndUserIdIsNotNull(11L) } returns 0L
        every { cohorts.countBySubjectId(10L) } returns 1L
        every { cohorts.countBySubjectId(11L) } returns 2L

        val result = service.summaries()

        assertThat(result).hasSize(2)
        val a = result.first { it.subject.id == 10L }
        val b = result.first { it.subject.id == 11L }
        assertThat(a.memberCount).isEqualTo(7)
        assertThat(a.mappingCount).isEqualTo(1)
        assertThat(b.memberCount).isEqualTo(0)
        assertThat(b.mappingCount).isEqualTo(2)
    }

    // ── detail(): the ledger, folded ─────────────────────────────────────────────────
    //
    // The page reads membership and its agreement with the external system from one payload,
    // so every row comes back — including the ones with no local account — carrying the state
    // it is in.

    @Test
    fun `detail reports the state each row is in`() {
        val subject = subject(20L)
        val cohort = cohort(200L)
        val desired = member(cohort, subject, userId = 5L)
        val synced = member(cohort, subject, userId = 6L, syncedAt = NOW)
        val verified = member(cohort, subject, userId = 7L, syncedAt = NOW, verifiedAt = NOW)
        stubDetail(subject, cohort, listOf(desired, synced, verified))
        every { users.findAllByIds(any()) } returns emptyList()
        every { users.isSoftDeleted(any()) } returns false
        every { externalIds.findByExternalIds(any(), any(), any()) } returns emptyList()

        val detail = service.detail(20L)

        // The page reads whether a row is in step with the target off the row itself, rather
        // than from a second call that classifies the same rows again.
        assertThat(detail.members.map { it.state }).containsExactlyInAnyOrder(
            CohortMemberState.DESIRED,
            CohortMemberState.SYNCED,
            CohortMemberState.VERIFIED,
        )
        assertThat(detail.members.map { it.system }.distinct()).containsExactly(TargetSystem.BREVO)
    }

    @Test
    fun `detail returns rows present externally with no local account`() {
        val subject = subject(24L)
        val cohort = cohort(240L)
        val member = member(cohort, subject, userId = 5L)
        val stranger = member(cohort, subject, userId = null, externalUserId = "ext-9", verifiedAt = NOW, label = "someone@example.com")
        stubDetail(subject, cohort, listOf(member, stranger))
        every { users.findAllByIds(any()) } returns emptyList()
        every { users.isSoftDeleted(any()) } returns false
        every { externalIds.findByExternalIds(any(), any(), any()) } returns emptyList()

        val rows = service.detail(24L).members

        // The old member query filtered these out for having no user, which is the one thing
        // that makes them worth showing.
        assertThat(rows).hasSize(2)
        val strangerRow = rows.single { it.member.userId == null }
        assertThat(strangerRow.state).isEqualTo(CohortMemberState.STRANGER)
        assertThat(strangerRow.member.externalUserId).isEqualTo("ext-9")
        assertThat(strangerRow.member.label).isEqualTo("someone@example.com")
    }

    @Test
    fun `detail names the account behind a stranger's external id`() {
        val subject = subject(21L)
        val cohort = cohort(210L)
        val stranger = member(cohort, subject, userId = null, externalUserId = "ext-42", verifiedAt = NOW)
        stubDetail(subject, cohort, listOf(stranger))
        every { externalIds.findByExternalIds(any(), "BREVO", setOf("ext-42")) } returns
            listOf(ExternalIdMapping(aggregateType = "USER", aggregateId = 77L, system = "BREVO", externalId = "ext-42"))
        every { users.findAllByIds(listOf(77L)) } returns listOf(user(77L, "Emma Dokter"))
        every { users.isSoftDeleted(any()) } returns false

        val row = service.detail(21L).members.single()

        // Without this the page can only show an opaque external id for somebody it knows.
        assertThat(row.resolvedUserId).isEqualTo(77L)
        assertThat(row.user?.fullName).isEqualTo("Emma Dokter")
    }

    @Test
    fun `detail leaves a stranger nameless when no account claims its external id`() {
        val subject = subject(25L)
        val cohort = cohort(250L)
        stubDetail(subject, cohort, listOf(member(cohort, subject, userId = null, externalUserId = "ext-unknown", verifiedAt = NOW)))
        every { externalIds.findByExternalIds(any(), any(), any()) } returns emptyList()
        every { users.findAllByIds(any()) } returns emptyList()
        every { users.isSoftDeleted(any()) } returns false

        val row = service.detail(25L).members.single()

        assertThat(row.resolvedUserId).isNull()
        assertThat(row.user).isNull()
    }

    @Test
    fun `detail reports a mapping's newest confirmation as when it was last reconciled`() {
        val subject = subject(22L)
        val cohort = cohort(220L)
        val older = member(cohort, subject, userId = 1L, syncedAt = NOW, verifiedAt = NOW.minusDays(3))
        val newest = member(cohort, subject, userId = 2L, syncedAt = NOW, verifiedAt = NOW)
        stubDetail(subject, cohort, listOf(older, newest))
        every { users.findAllByIds(any()) } returns emptyList()
        every { users.isSoftDeleted(any()) } returns false
        every { externalIds.findByExternalIds(any(), any(), any()) } returns emptyList()

        val mapping = service.detail(22L).mappings.single()

        assertThat(mapping.lastReconciledAt).isEqualTo(NOW.toInstant(ZoneOffset.UTC))
    }

    @Test
    fun `detail places a mapping under the system that holds it, and the folder it is filed in`() {
        val subject = subject(26L)
        val cohort = cohort(260L).apply { folder = "Committees" }
        stubDetail(subject, cohort, emptyList())
        stubNoUsers()

        val mapping = service.detail(26L).mappings.single()

        // Outside in, and the system named the way an operator sees it rather than as an enum.
        assertThat(mapping.path).containsExactly("Brevo", "Committees")
    }

    @Test
    fun `detail places an unfiled mapping directly under its system`() {
        val subject = subject(27L)
        val cohort = cohort(270L).apply { folder = null }
        stubDetail(subject, cohort, emptyList())
        stubNoUsers()

        // No folder is not an anonymous folder: the path is one step, not two.
        assertThat(service.detail(27L).mappings.single().path).containsExactly("Brevo")
    }

    @Test
    fun `detail ignores a folder recorded as blank`() {
        val subject = subject(28L)
        val cohort = cohort(280L).apply { folder = "   " }
        stubDetail(subject, cohort, emptyList())
        stubNoUsers()

        assertThat(service.detail(28L).mappings.single().path).containsExactly("Brevo")
    }

    @Test
    fun `detail still names a system that has no strategy registered`() {
        val subject = subject(29L)
        val cohort = Cohort(system = "GOOGLE_CALENDAR", kind = CohortKind.LIST, label = "Gone")
            .apply { id = 290L }
        stubDetail(subject, cohort, emptyList())
        stubNoUsers()

        // A cohort can outlive the adapter that made it. Without a strategy there is no
        // human label to use, so the row falls back to the system's own name rather than
        // losing its place.
        assertThat(service.detail(29L).mappings.single().path).containsExactly("GOOGLE_CALENDAR")
    }

    @Test
    fun `detail leaves out a cohort pointing at a system this build does not have`() {
        val subject = subject(30L)
        val known = cohort(300L)
        val unknown = Cohort(system = "DISCORD", kind = CohortKind.LIST, label = "Gone")
            .apply { id = 301L }
        every { subjects.findById(30L) } returns Optional.of(subject)
        every { cohorts.findAllBySubjectId(30L) } returns listOf(known, unknown)
        every { targetIds.find(known) } returns "external-1"
        every { cohortMembers.findAllBySubjectId(30L) } returns emptyList()
        every { cohortMembers.findAllByCohortId(any()) } returns emptyList()
        stubNoUsers()

        val detail = service.detail(30L)

        // Nothing on that row would work without its system, and one dead row is not worth
        // the whole page.
        assertThat(detail.mappings).extracting<Long> { it.cohort.id }.containsExactly(300L)
    }

    @Test
    fun `detail still lists the members of a cohort whose system is gone`() {
        val subject = subject(31L)
        val unknown = Cohort(system = "DISCORD", kind = CohortKind.LIST, label = "Gone")
            .apply { id = 310L }
        val row = member(unknown, subject, userId = 1L)
        every { subjects.findById(31L) } returns Optional.of(subject)
        every { cohorts.findAllBySubjectId(31L) } returns listOf(unknown)
        every { cohortMembers.findAllBySubjectId(31L) } returns listOf(row)
        every { cohortMembers.findAllByCohortId(any()) } returns listOf(row)
        every { users.findAllByIds(any()) } returns listOf(user(1L, "Emma Dokter"))
        every { users.isSoftDeleted(any()) } returns false
        every { externalIds.findByExternalIds(any(), any(), any()) } returns emptyList()

        val detail = service.detail(31L)

        // Dropping the target must not drop the people in it: they are still on the ledger,
        // and hiding them would make the page lie about who is in the cohort.
        assertThat(detail.mappings).isEmpty()
        assertThat(detail.members).hasSize(1)
        assertThat(detail.members.single().system).isNull()
    }

    @Test
    fun `detail leaves last reconciled null for a cohort never confirmed`() {
        val subject = subject(23L)
        val cohort = cohort(230L)
        stubDetail(subject, cohort, listOf(member(cohort, subject, userId = 1L)))
        every { users.findAllByIds(any()) } returns emptyList()
        every { users.isSoftDeleted(any()) } returns false
        every { externalIds.findByExternalIds(any(), any(), any()) } returns emptyList()

        assertThat(service.detail(23L).mappings.single().lastReconciledAt).isNull()
    }

    private fun stubNoUsers() {
        every { users.findAllByIds(any()) } returns emptyList()
        every { users.isSoftDeleted(any()) } returns false
        every { externalIds.findByExternalIds(any(), any(), any()) } returns emptyList()
    }

    private fun stubDetail(subject: CohortSubject, cohort: Cohort, rows: List<CohortMember>) {
        every { subjects.findById(subject.id!!) } returns Optional.of(subject)
        every { cohorts.findAllBySubjectId(subject.id!!) } returns listOf(cohort)
        every { targetIds.find(cohort) } returns "external-1"
        every { cohortMembers.findAllBySubjectId(subject.id!!) } returns rows
        every { cohortMembers.findAllByCohortId(cohort.id!!) } returns rows
    }

    private fun subject(id: Long): CohortSubject =
        CohortSubject(CohortSubjectType.NEWSLETTER_SUBSCRIBERS, "Test Subject $id").apply { this.id = id }

    private fun cohort(id: Long): Cohort =
        Cohort(system = "BREVO", kind = CohortKind.LIST, label = "Cohort $id").apply { this.id = id }

    private fun member(
        cohort: Cohort,
        subject: CohortSubject,
        userId: Long?,
        externalUserId: String? = null,
        syncedAt: LocalDateTime? = null,
        verifiedAt: LocalDateTime? = null,
        label: String? = null,
    ): CohortMember = CohortMember(
        cohort = cohort,
        userId = userId,
        subject = subject,
        externalUserId = externalUserId,
        syncedAt = syncedAt,
        verifiedAt = verifiedAt,
        label = label,
    ).apply { id = MEMBER_IDS++ }

    private fun user(id: Long, fullName: String): User =
        mockk<User>(relaxed = true).also {
            every { it.id } returns id
            every { it.fullName } returns fullName
            every { it.email } returns "user$id@example.com"
        }

    private companion object {
        val NOW: LocalDateTime = LocalDateTime.of(2026, 3, 1, 12, 0)
        var MEMBER_IDS = 1L
    }
}
