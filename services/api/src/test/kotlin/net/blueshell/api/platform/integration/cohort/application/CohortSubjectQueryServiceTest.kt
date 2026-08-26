package net.blueshell.api.platform.integration.cohort.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.cohort.persistence.Cohort
import net.blueshell.api.platform.integration.cohort.persistence.CohortKind
import net.blueshell.api.platform.integration.cohort.persistence.CohortMember
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubject
import net.blueshell.api.platform.integration.cohort.persistence.CohortSubjectType
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortMemberRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortRepository
import net.blueshell.api.platform.integration.cohort.persistence.repository.CohortSubjectRepository
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
    private val service = CohortSubjectQueryService(subjects, cohorts, cohortMembers, users, targetIds)

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
    fun `detail reports a mapping's newest confirmation as when it was last reconciled`() {
        val subject = subject(22L)
        val cohort = cohort(220L)
        val older = member(cohort, subject, userId = 1L, syncedAt = NOW, verifiedAt = NOW.minusDays(3))
        val newest = member(cohort, subject, userId = 2L, syncedAt = NOW, verifiedAt = NOW)
        stubDetail(subject, cohort, listOf(older, newest))
        every { users.findAllByIds(any()) } returns emptyList()
        every { users.isSoftDeleted(any()) } returns false

        val mapping = service.detail(22L).mappings.single()

        assertThat(mapping.lastReconciledAt).isEqualTo(NOW.toInstant(ZoneOffset.UTC))
    }

    @Test
    fun `detail leaves last reconciled null for a cohort never confirmed`() {
        val subject = subject(23L)
        val cohort = cohort(230L)
        stubDetail(subject, cohort, listOf(member(cohort, subject, userId = 1L)))
        every { users.findAllByIds(any()) } returns emptyList()
        every { users.isSoftDeleted(any()) } returns false

        assertThat(service.detail(23L).mappings.single().lastReconciledAt).isNull()
    }

    private fun stubDetail(subject: CohortSubject, cohort: Cohort, rows: List<CohortMember>) {
        every { subjects.findById(subject.id!!) } returns Optional.of(subject)
        every { cohorts.findAllBySubjectId(subject.id!!) } returns listOf(cohort)
        every { targetIds.find(cohort) } returns "external-1"
        every { cohortMembers.findAllBySubjectIdAndUserIdIsNotNull(subject.id!!) } returns rows
        every { cohortMembers.findAllByCohortId(cohort.id!!) } returns rows
    }

    private fun subject(id: Long): CohortSubject =
        CohortSubject(CohortSubjectType.CUSTOM, "Test Subject $id").apply { this.id = id }

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
