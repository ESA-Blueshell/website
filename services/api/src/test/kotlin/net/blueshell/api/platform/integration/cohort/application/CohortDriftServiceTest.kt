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
import net.blueshell.api.platform.integration.sync.application.ExternalIdMappingService
import net.blueshell.api.platform.integration.sync.persistence.ExternalIdMapping
import net.blueshell.api.shared.enums.TargetSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class CohortDriftServiceTest {

    private val cohorts: CohortRepository = mockk()
    private val members: CohortMemberRepository = mockk()
    private val externalIds: ExternalIdMappingService = mockk()
    private val targetIds: CohortTargetIds = mockk()
    private val users: UserService = mockk()
    private val service = CohortDriftService(cohorts, members, externalIds, targetIds, users)

    @Test
    fun `compute classifies ledger rows without external port calls`() {
        val subject = subject(7L)
        val cohort = cohort(99L, subject.id!!)
        val lastObserved = LocalDateTime.parse("2026-05-03T10:15:30")
        every { cohorts.findBySubjectIdAndSystem(7L, TargetSystem.BREVO) } returns cohort
        every { targetIds.find(any()) } returns "list-99"
        every { members.findAllByCohortId(99L) } returns listOf(
            member(cohort, subject, userId = 1L),
            member(cohort, subject, userId = 2L),
            member(cohort, subject, userId = 9L, syncedAt = lastObserved, verifiedAt = lastObserved),
            member(cohort, subject, userId = null, externalUserId = "ext-active", verifiedAt = lastObserved),
            member(cohort, subject, userId = null, externalUserId = "ext-soft", verifiedAt = lastObserved.minusDays(1)),
            member(cohort, subject, userId = null, externalUserId = "ext-unknown", verifiedAt = lastObserved.minusDays(2)),
        )
        every {
            externalIds.findBatch("USER", setOf(1L, 2L), TargetSystem.BREVO.name)
        } returns listOf(ExternalIdMapping("USER", 1L, TargetSystem.BREVO.name, "ext-1"))
        every {
            externalIds.findByExternalIds(
                "USER",
                TargetSystem.BREVO.name,
                setOf("ext-active", "ext-soft", "ext-unknown"),
            )
        } returns listOf(
            ExternalIdMapping("USER", 10L, TargetSystem.BREVO.name, "ext-active"),
            ExternalIdMapping("USER", 20L, TargetSystem.BREVO.name, "ext-soft"),
        )
        every { users.findAllByIds(match { it.toSet() == setOf(10L, 20L) }) } returns listOf(user(10L))
        every { users.findSoftDeletedIds(setOf(20L)) } returns setOf(20L)

        val report = service.compute(7L, TargetSystem.BREVO)

        assertThat(report.externalCohortId).isEqualTo("list-99")
        assertThat(report.missing).containsExactlyInAnyOrder(
            MissingRow(userId = 1L, hasExternalMapping = true),
            MissingRow(userId = 2L, hasExternalMapping = false),
        )
        assertThat(report.extras).containsExactlyInAnyOrder(
            ExtraRow(
                externalUserId = "ext-active",
                label = null,
                kind = DriftExtraKind.KNOWN_LOCAL_USER,
                userId = 10L,
                fullName = "Ada Lovelace",
                email = "ada@example.test",
                softDeleted = false,
            ),
            ExtraRow(
                externalUserId = "ext-soft",
                label = null,
                kind = DriftExtraKind.KNOWN_LOCAL_USER,
                userId = 20L,
                fullName = null,
                email = null,
                softDeleted = true,
            ),
            ExtraRow(
                externalUserId = "ext-unknown",
                label = null,
                kind = DriftExtraKind.UNKNOWN_EXTERNAL,
            ),
        )
        assertThat(report.lastReconciledAt).isEqualTo(lastObserved.toInstant(ZoneOffset.UTC))
        verify(exactly = 0) { users.isSoftDeleted(any()) }
    }

    @Test
    fun `compute returns not materialised when the cohort has no external mapping`() {
        val subject = subject(7L)
        val cohort = cohort(99L, subject.id!!)
        every { cohorts.findBySubjectIdAndSystem(7L, TargetSystem.BREVO) } returns cohort
        every { targetIds.find(any()) } returns null

        val report = service.compute(7L, TargetSystem.BREVO)

        assertThat(report).isEqualTo(DriftReport.notMaterialised(99L, TargetSystem.BREVO))
        verify(exactly = 0) { members.findAllByCohortId(any()) }
    }

    private fun subject(id: Long): CohortSubject =
        CohortSubject(CohortSubjectType.CUSTOM, "Members").apply { this.id = id }

    private fun cohort(id: Long, subjectId: Long): Cohort =
        Cohort(
            system = TargetSystem.BREVO,
            kind = CohortKind.LIST,
            label = "Members",
            subjectId = subjectId,
        ).apply { this.id = id }

    private fun member(
        cohort: Cohort,
        subject: CohortSubject,
        userId: Long?,
        externalUserId: String? = null,
        syncedAt: LocalDateTime? = null,
        verifiedAt: LocalDateTime? = null,
    ): CohortMember =
        CohortMember(
            cohort = cohort,
            userId = userId,
            subject = subject,
            externalUserId = externalUserId,
            syncedAt = syncedAt,
            verifiedAt = verifiedAt,
        )

    private fun user(id: Long): User =
        mockk<User>().also {
            every { it.id } returns id
            every { it.fullName } returns "Ada Lovelace"
            every { it.email } returns "ada@example.test"
        }
}
