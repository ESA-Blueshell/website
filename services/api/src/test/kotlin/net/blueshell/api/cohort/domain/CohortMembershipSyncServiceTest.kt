package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Optional

class CohortMembershipSyncServiceTest {
    private val targets: TargetRepository = mockk()
    private val ledger: CohortLedger = mockk(relaxed = true)
    private val target42 = ExternalTarget(TargetSystem.BREVO, "42", TargetKind.LIST, "42")
    private val brevoTarget: TargetStrategy =
        mockk(relaxed = true) {
            every { system } returns TargetSystem.BREVO
            every { handle("42") } returns target42
            every { available() } returns true
            every { makesMemberIds } returns true
        }
    private val targetExternalIds: CohortTargetIds = mockk(relaxed = true)
    private val service =
        CohortMembershipSyncService(
            targets = targets,
            ledger = ledger,
            strategies = TargetStrategies(listOf(brevoTarget)),
            targetExternalIds = targetExternalIds,
            // A relaxed manager still runs the TransactionTemplate callbacks; the
            // real no-active-transaction guarantee is asserted in
            // CohortProviderTransactionBoundaryIT against a real transaction manager.
            transactionManager = mockk(relaxed = true),
        )

    init {
        every { ledger.markPushed(any(), any(), any(), any()) } returns true
    }

    @Test
    fun `ADD calls the strategy when both external ids exist`() {
        givenTarget(id = 10L, system = "BREVO", label = "Members")
        every { brevoTarget.memberIds(setOf(1L)) } returns mapOf(1L to "777")
        every { targetExternalIds.find(any()) } returns "42"

        service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.ADD)

        verify { brevoTarget.add(target42, "777") }
    }

    @Test
    fun `ADD without a cohort target fails terminally and does not enqueue materialization`() {
        givenTarget(id = 10L, system = "BREVO", label = "Members")
        every { brevoTarget.memberIds(setOf(1L)) } returns mapOf(1L to "777")
        every { targetExternalIds.find(any()) } returns null

        assertThatThrownBy {
            service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.ADD)
        }.isInstanceOf(NonRetryableJobException::class.java)
            .hasMessageContaining("cohort 10 has no BREVO target")

        verify(exactly = 0) { brevoTarget.add(any(), any()) }
        verify(exactly = 0) { brevoTarget.create(any(), any()) }
    }

    @Test
    fun `ADD marks the desired row pushed after a successful external add`() {
        givenTarget(id = 10L, system = "BREVO", label = "Members")
        every { brevoTarget.memberIds(setOf(1L)) } returns mapOf(1L to "777")
        every { targetExternalIds.find(any()) } returns "42"

        service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.ADD)

        verify { brevoTarget.add(target42, "777") }
        verify { ledger.markPushed(10L, 1L, "777", any()) }
    }

    @Test
    fun `ADD whose desired row is gone by the time it lands still pushes, and stamps nothing`() {
        givenTarget(id = 10L, system = "BREVO", label = "Members")
        every { brevoTarget.memberIds(setOf(1L)) } returns mapOf(1L to "777")
        every { targetExternalIds.find(any()) } returns "42"
        every { ledger.markPushed(10L, 1L, "777", any()) } returns false

        service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.ADD)

        verify { brevoTarget.add(target42, "777") }
    }

    @Test
    fun `ADD without a user external id has the strategy make it and throws retryable`() {
        givenTarget(id = 10L, system = "BREVO", label = "Members")
        every { brevoTarget.memberIds(setOf(1L)) } returns emptyMap()

        assertThatThrownBy {
            service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.ADD)
        }.isInstanceOf(CohortMembershipNotReadyException::class.java)

        verify { brevoTarget.makeMemberId(1L) }
        verify(exactly = 0) { brevoTarget.add(any(), any()) }
    }

    @Test
    fun `ADD for somebody with no account on a system that makes none says they are unreachable`() {
        givenTarget(id = 10L, system = "BREVO", label = "Members")
        every { brevoTarget.memberIds(setOf(1L)) } returns emptyMap()
        every { brevoTarget.makesMemberIds } returns false

        assertThat(service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.ADD))
            .isEqualTo("The user has no Brevo account linked, so cannot be reached.")
        verify(exactly = 0) { brevoTarget.makeMemberId(any()) }
        verify(exactly = 0) { brevoTarget.add(any(), any()) }
    }

    @Test
    fun `a system that cannot be reached skips both writes`() {
        givenTarget(id = 10L, system = "BREVO", label = "Members")
        every { brevoTarget.available() } returns false

        assertThat(service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.ADD))
            .isEqualTo("Brevo cannot be reached now; the next reconcile catches up.")
        assertThat(service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.REMOVE)).isNotNull()
        verify(exactly = 0) { brevoTarget.add(any(), any()) }
        verify(exactly = 0) { brevoTarget.remove(any(), any()) }
    }

    @Test
    fun `REMOVE calls the strategy when both external ids exist`() {
        givenTarget(id = 10L, system = "BREVO", label = "Members")
        every { brevoTarget.memberIds(setOf(1L)) } returns mapOf(1L to "777")
        every { targetExternalIds.find(any()) } returns "42"

        assertThat(service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.REMOVE)).isNull()

        verify { brevoTarget.remove(target42, "777") }
    }

    @Test
    fun `REMOVE is a no-op when an external id is missing, and says which`() {
        givenTarget(id = 10L, system = "BREVO", label = "Members")
        every { brevoTarget.memberIds(setOf(1L)) } returns emptyMap()
        every { targetExternalIds.find(any()) } returns null

        assertThat(service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.REMOVE))
            .isEqualTo("The user has no BREVO account, so holds no BREVO target.")
        every { brevoTarget.memberIds(setOf(1L)) } returns mapOf(1L to "777")
        assertThat(service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.REMOVE))
            .isEqualTo("The cohort has no BREVO list linked.")

        verify(exactly = 0) { brevoTarget.remove(any(), any()) }
        verify(exactly = 0) { brevoTarget.add(any(), any()) }
    }

    @Test
    fun `unknown cohort id throws NonRetryableJobException`() {
        every { targets.findById(10L) } returns Optional.empty()

        assertThatThrownBy {
            service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.ADD)
        }.isInstanceOf(NonRetryableJobException::class.java)
    }

    @Test
    fun `unknown system on cohort throws NonRetryableJobException`() {
        givenTarget(id = 10L, system = "MARS_NETWORK", label = "Settlers")

        assertThatThrownBy {
            service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.ADD)
        }.isInstanceOf(NonRetryableJobException::class.java)
    }

    @Test
    fun `cohort whose system has no registered strategy throws NonRetryableJobException`() {
        // Target's system is a valid TargetSystem value but no matching TargetStrategy bean
        // exists (GOOGLE_CALENDAR has none yet).
        givenTarget(id = 10L, system = "GOOGLE_CALENDAR", label = "events")

        assertThatThrownBy {
            service.sync(userId = 1L, targetId = 10L, intent = SyncCohortMembershipIntent.ADD)
        }.isInstanceOf(NonRetryableJobException::class.java)
            .hasMessageContaining("No TargetStrategy")
    }

    private fun givenTarget(
        id: Long,
        system: String,
        label: String,
    ) {
        val c = Entities.target()
        c.id = id
        c.system = system
        c.label = label
        c.kind = TargetKind.LIST
        every { targets.findById(id) } returns Optional.of(c)
    }
}
