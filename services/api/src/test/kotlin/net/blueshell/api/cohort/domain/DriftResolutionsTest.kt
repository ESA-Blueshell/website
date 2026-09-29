package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.blueshell.api.cohort.persistence.CohortMember
import net.blueshell.api.cohort.persistence.CohortMemberRepository
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.DriftResolution
import net.blueshell.api.cohort.persistence.DriftResolutionAction
import net.blueshell.api.cohort.persistence.DriftResolutionRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorProvider
import net.blueshell.api.sync.api.ExternalIdConflictException
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional

class DriftResolutionsTest {
    private val cohorts: CohortRepository = mockk()
    private val members: CohortMemberRepository = mockk()
    private val resolutions: DriftResolutionRepository = mockk(relaxed = true)
    private val remediation: CohortRemediation = mockk(relaxed = true)
    private val users: UserService = mockk()
    private val jobs: JobQueue = mockk(relaxed = true)
    private val actors: ActorProvider = mockk()
    private val service = DriftResolutions(cohorts, members, resolutions, remediation, users, jobs, actors)

    private val subject = Entities.cohortSubject(id = 1L)
    private val cohort = Entities.cohort(id = 2L, subjectId = 1L, externalId = "list-2")
    private val saved = slot<List<DriftResolution>>()

    init {
        every { cohorts.findById(2L) } returns Optional.of(cohort)
        every { actors.currentOrSystem() } returns Actor.user(9L, Role.BOARD)
        every { resolutions.saveAll(capture(saved)) } answers { firstArg() }
    }

    @Test
    fun `push queues only the ours-only people asked for and records each`() {
        every { members.findAllByCohortIdAndUserIdIsNotNull(2L) } returns
            listOf(
                row(userId = 5L),
                row(userId = 6L, syncedAt = NOW),
                row(userId = 7L, syncedAt = NOW, verifiedAt = NOW),
                row(userId = 8L),
            )

        val pushed = service.push(1L, 2L, listOf(5L, 6L, 7L))

        assertThat(pushed).isEqualTo(2)
        verify(exactly = 2) { jobs.runAsync(CohortJobs.SyncCohortMembership, any(), any()) }
        assertThat(saved.captured.map { it.userId }).containsExactly(5L, 6L)
        assertThat(saved.captured.map { it.action }).containsOnly(DriftResolutionAction.PUSH)
        assertThat(saved.captured.map { it.resolvedBy }).containsOnly(9L)
    }

    @Test
    fun `remove queues the removal of each theirs-only person and records it`() {
        every { members.findAllByCohortIdAndExternalUserIdInAndUserIdIsNull(2L, listOf("ext-1")) } returns
            listOf(row(userId = null, externalUserId = "ext-1", label = "c@example.com"))

        assertThat(service.remove(1L, 2L, listOf("ext-1"))).isEqualTo(1)

        verify { jobs.runAsync(CohortJobs.RemoveExternalMember, CohortJobs.RemoveExternalMemberPayload(2L, "ext-1"), any()) }
        assertThat(saved.captured.single().label).isEqualTo("c@example.com")
    }

    @Test
    fun `nothing left to resolve records nothing`() {
        every { members.findAllByCohortIdAndExternalUserIdInAndUserIdIsNull(2L, listOf("gone")) } returns emptyList()

        assertThat(service.remove(1L, 2L, listOf("gone"))).isZero()
        verify(exactly = 0) { resolutions.saveAll(any<List<DriftResolution>>()) }
    }

    @Test
    fun `a link proposal names the account with the contact's address`() {
        every { members.findAllByCohortIdAndExternalUserIdInAndUserIdIsNull(2L, listOf("ext-1", "ext-2")) } returns
            listOf(
                row(userId = null, externalUserId = "ext-1", label = " Ada@Example.com"),
                row(userId = null, externalUserId = "ext-2", label = "nobody@example.com"),
            )
        every { users.findAllByEmails(listOf(" Ada@Example.com", "nobody@example.com")) } returns
            listOf(Entities.user(id = 5L, email = "ada@example.com", firstName = "Ada", lastName = "Lovelace"))

        val proposals = service.proposeLinks(1L, 2L, listOf("ext-1", "ext-2"))

        assertThat(proposals).containsExactly(
            LinkProposal("ext-1", " Ada@Example.com", 5L, "Ada Lovelace"),
            LinkProposal("ext-2", "nobody@example.com", null, null),
        )
    }

    @Test
    fun `a link to a contact another account holds is reported and the rest are linked`() {
        every { members.findAllByCohortIdAndExternalUserIdInAndUserIdIsNull(2L, listOf("ext-1", "ext-2")) } returns
            listOf(row(userId = null, externalUserId = "ext-2", label = "b@example.com"))
        every { remediation.linkUser(1L, 5L, TargetSystem.BREVO, "ext-1") } throws
            ExternalIdConflictException(4L, TargetSystem.BREVO, "ext-1")

        val outcome = service.link(1L, 2L, listOf(LinkChoice("ext-1", 5L), LinkChoice("ext-2", 6L)))

        assertThat(outcome).isEqualTo(LinkOutcome(1, listOf(LinkConflict("ext-1", 4L))))
        assertThat(saved.captured.single().externalUserId).isEqualTo("ext-2")
        assertThat(saved.captured.single().label).isEqualTo("b@example.com")
    }

    @Test
    fun `a target of another cohort, or one not yet created, is refused`() {
        every { cohorts.findById(3L) } returns Optional.of(Entities.cohort(id = 3L, subjectId = 99L, externalId = "x"))
        every { cohorts.findById(4L) } returns Optional.of(Entities.cohort(id = 4L, subjectId = 1L))
        every { cohorts.findById(5L) } returns Optional.empty()

        assertThatThrownBy { service.push(1L, 3L, listOf(1L)) }.isInstanceOf(TargetNotOfCohort::class.java)
        assertThatThrownBy { service.push(1L, 5L, listOf(1L)) }.isInstanceOf(TargetNotOfCohort::class.java)
        assertThatThrownBy { service.remove(1L, 4L, listOf("x")) }.isInstanceOf(TargetNotCreated::class.java)
    }

    @Test
    fun `enforcing a target switches it, and switching back undoes it`() {
        service.enforce(1L, 2L, true)
        assertThat(cohort.enforced).isTrue()

        service.enforce(1L, 2L, false)
        assertThat(cohort.enforced).isFalse()
    }

    private fun row(
        userId: Long?,
        externalUserId: String? = null,
        syncedAt: LocalDateTime? = null,
        verifiedAt: LocalDateTime? = null,
        label: String? = null,
    ) = CohortMember(cohort, userId, subject, externalUserId, syncedAt, verifiedAt, label)

    private companion object {
        val NOW: LocalDateTime = LocalDateTime.of(2026, 9, 29, 20, 0)
    }
}
