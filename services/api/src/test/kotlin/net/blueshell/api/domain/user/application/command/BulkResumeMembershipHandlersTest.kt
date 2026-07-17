package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.command.ExecuteBulkResumeMembershipCommand
import net.blueshell.api.domain.user.command.PreviewBulkResumeMembershipCommand
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.dto.bulk.BulkActionType
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.enums.MemberType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

class BulkResumeMembershipHandlersTest {

    private val userService = mock<UserService>()
    private val membershipService = mock<MembershipService>()
    private val periodService = mock<ContributionPeriodService>()

    // Helper: basis period Jan 1 – Dec 31 2024
    private val basisPeriodStart = LocalDate.of(2024, 1, 1)
    private val basisPeriodEnd = LocalDate.of(2024, 12, 31)

    private fun basisPeriod() = mockPeriod(100L, basisPeriodStart, basisPeriodEnd)

    @Nested
    inner class PreviewBulkResumeMembership {

        private val handler = PreviewBulkResumeMembershipHandler(membershipService, userService, periodService)

        @Test
        fun `returns RESUME outcome when latest membership ended within basis period`() {
            val userId = 1L
            val user = mockUser(userId, "Alice")
            val membership = mockMembership(
                memberType = MemberType.REGULAR,
                startDate = LocalDate.of(2023, 9, 1),
                endDate = LocalDate.of(2024, 6, 30), // within basis period
            )
            whenever(userService.findById(userId)).thenReturn(user)
            whenever(membershipService.findByUserId(userId)).thenReturn(mutableListOf(membership))
            whenever(periodService.findLatest()).thenReturn(basisPeriod())

            val result = handler.handle(PreviewBulkResumeMembershipCommand(listOf(userId)))

            assertThat(result.action).isEqualTo(BulkActionType.RESUME_MEMBERSHIP)
            assertThat(result.rows).hasSize(1)
            val row = result.rows[0]
            assertThat(row.disposition).isEqualTo(BulkRowDisposition.INCLUDED)
            assertThat(row.reason).isEqualTo(BulkRowReason.WILL_RESUME)
            assertThat(row.memberType).isEqualTo(MemberType.REGULAR)
        }

        @Test
        fun `returns START_NEW outcome when latest membership ended before basis period`() {
            val userId = 2L
            val user = mockUser(userId, "Bob")
            val membership = mockMembership(
                memberType = MemberType.ALUMNI,
                startDate = LocalDate.of(2022, 1, 1),
                endDate = LocalDate.of(2022, 12, 31), // before basis period
                incasso = false,
            )
            whenever(userService.findById(userId)).thenReturn(user)
            whenever(membershipService.findByUserId(userId)).thenReturn(mutableListOf(membership))
            whenever(periodService.findLatest()).thenReturn(basisPeriod())

            val result = handler.handle(PreviewBulkResumeMembershipCommand(listOf(userId)))

            val row = result.rows[0]
            assertThat(row.disposition).isEqualTo(BulkRowDisposition.INCLUDED)
            assertThat(row.reason).isEqualTo(BulkRowReason.WILL_START_NEW)
            assertThat(row.memberType).isEqualTo(MemberType.ALUMNI) // copied from prior
        }

        @Test
        fun `returns START_NEW with REGULAR when no prior membership exists`() {
            val userId = 3L
            val user = mockUser(userId, "Charlie")
            whenever(userService.findById(userId)).thenReturn(user)
            whenever(membershipService.findByUserId(userId)).thenReturn(mutableListOf())
            whenever(periodService.findLatest()).thenReturn(basisPeriod())

            val result = handler.handle(PreviewBulkResumeMembershipCommand(listOf(userId)))

            val row = result.rows[0]
            assertThat(row.disposition).isEqualTo(BulkRowDisposition.INCLUDED)
            assertThat(row.reason).isEqualTo(BulkRowReason.WILL_START_NEW)
            assertThat(row.memberType).isEqualTo(MemberType.REGULAR)
        }

        @Test
        fun `skips user with already-active membership`() {
            val userId = 4L
            val user = mockUser(userId, "Diana")
            val activeMembership = mockMembership(endDate = null) // active
            whenever(userService.findById(userId)).thenReturn(user)
            whenever(membershipService.findByUserId(userId)).thenReturn(mutableListOf(activeMembership))
            whenever(periodService.findLatest()).thenReturn(basisPeriod())

            val result = handler.handle(PreviewBulkResumeMembershipCommand(listOf(userId)))

            val row = result.rows[0]
            assertThat(row.disposition).isEqualTo(BulkRowDisposition.SKIPPED)
            assertThat(row.reason).isEqualTo(BulkRowReason.ALREADY_ACTIVE)
        }

        @Test
        fun `skips all when no contribution period exists`() {
            val userId = 5L
            val user = mockUser(userId, "Eve")
            whenever(userService.findById(userId)).thenReturn(user)
            whenever(periodService.findLatest()).thenReturn(null)

            val result = handler.handle(PreviewBulkResumeMembershipCommand(listOf(userId)))

            assertThat(result.rows).hasSize(1)
            assertThat(result.rows[0].disposition).isEqualTo(BulkRowDisposition.SKIPPED)
            assertThat(result.rows[0].reason).isEqualTo(BulkRowReason.NO_CONTRIBUTION_PERIOD)
        }
    }

    @Nested
    inner class ExecuteBulkResumeMembership {

        private val handler = ExecuteBulkResumeMembershipHandler(membershipService, userService, periodService)

        @Test
        fun `resumes membership by clearing endDate`() {
            val userId = 1L
            val user = mockUser(userId, "Alice")
            val membership = mockMembership(
                memberType = MemberType.REGULAR,
                startDate = LocalDate.of(2023, 9, 1),
                endDate = LocalDate.of(2024, 6, 30), // within basis period
            )
            whenever(userService.findById(userId)).thenReturn(user)
            whenever(membershipService.findByUserId(userId)).thenReturn(mutableListOf(membership))
            whenever(periodService.findLatest()).thenReturn(basisPeriod())

            val result = handler.handle(ExecuteBulkResumeMembershipCommand(listOf(userId)))

            assertThat(result.applied).isEqualTo(1)
            assertThat(result.skipped).isEqualTo(0)
            val captor = argumentCaptor<Membership>()
            verify(membershipService).update(captor.capture())
            assertThat(captor.firstValue.endDate).isNull()
        }

        @Test
        fun `inserts new membership copying memberType and incasso from prior`() {
            val userId = 2L
            val user = mockUser(userId, "Bob")
            val priorMembership = mockMembership(
                memberType = MemberType.ALUMNI,
                startDate = LocalDate.of(2022, 1, 1),
                endDate = LocalDate.of(2022, 12, 31),
                incasso = true,
            )
            whenever(userService.findById(userId)).thenReturn(user)
            whenever(membershipService.findByUserId(userId)).thenReturn(mutableListOf(priorMembership))
            whenever(periodService.findLatest()).thenReturn(basisPeriod())

            val result = handler.handle(ExecuteBulkResumeMembershipCommand(listOf(userId)))

            assertThat(result.applied).isEqualTo(1)
            val captor = argumentCaptor<Membership>()
            verify(membershipService).create(captor.capture())
            val created = captor.firstValue
            assertThat(created.memberType).isEqualTo(MemberType.ALUMNI)
            assertThat(created.incasso).isTrue()
            assertThat(created.startDate).isEqualTo(LocalDate.now())
            assertThat(created.endDate).isNull()
        }

        @Test
        fun `inserts REGULAR non-incasso membership when no prior membership`() {
            val userId = 3L
            val user = mockUser(userId, "Charlie")
            whenever(userService.findById(userId)).thenReturn(user)
            whenever(membershipService.findByUserId(userId)).thenReturn(mutableListOf())
            whenever(periodService.findLatest()).thenReturn(basisPeriod())

            val result = handler.handle(ExecuteBulkResumeMembershipCommand(listOf(userId)))

            assertThat(result.applied).isEqualTo(1)
            val captor = argumentCaptor<Membership>()
            verify(membershipService).create(captor.capture())
            val created = captor.firstValue
            assertThat(created.memberType).isEqualTo(MemberType.REGULAR)
            assertThat(created.incasso).isFalse()
        }

        @Test
        fun `skips user with already-active membership`() {
            val userId = 4L
            val user = mockUser(userId, "Diana")
            val activeMembership = mockMembership(endDate = null)
            whenever(userService.findById(userId)).thenReturn(user)
            whenever(membershipService.findByUserId(userId)).thenReturn(mutableListOf(activeMembership))
            whenever(periodService.findLatest()).thenReturn(basisPeriod())

            val result = handler.handle(ExecuteBulkResumeMembershipCommand(listOf(userId)))

            assertThat(result.applied).isEqualTo(0)
            assertThat(result.skipped).isEqualTo(1)
            verify(membershipService, never()).update(org.mockito.kotlin.any())
            verify(membershipService, never()).create(org.mockito.kotlin.any())
        }

        @Test
        fun `returns all skipped when no contribution period`() {
            val userId = 5L
            whenever(periodService.findLatest()).thenReturn(null)

            val result = handler.handle(ExecuteBulkResumeMembershipCommand(listOf(userId)))

            assertThat(result.applied).isEqualTo(0)
            assertThat(result.skipped).isEqualTo(1)
            verify(membershipService, never()).update(org.mockito.kotlin.any())
            verify(membershipService, never()).create(org.mockito.kotlin.any())
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun mockUser(id: Long, name: String): User = User(
        username = "user$id",
        email = "user$id@example.com",
        password = "hash",
        initials = name.take(1).uppercase(),
        firstName = name,
        lastName = "",
    ).apply {
        setField(this, "id", id)
    }

    private fun mockPeriod(id: Long, startDate: LocalDate, endDate: LocalDate): ContributionPeriod =
        ContributionPeriod(
            startDate = startDate,
            endDate = endDate,
        ).apply {
            setField(this, "id", id)
        }

    private fun mockMembership(
        memberType: MemberType = MemberType.REGULAR,
        startDate: LocalDate = LocalDate.of(2023, 1, 1),
        endDate: LocalDate? = null,
        incasso: Boolean = false,
    ): Membership = Membership(
        user = mock(),
        startDate = startDate,
        endDate = endDate,
        memberType = memberType,
        incasso = incasso,
    ).apply {
        setField(this, "createdAt", Instant.parse("2024-01-01T00:00:00Z"))
        setField(this, "updatedAt", Instant.parse("2024-01-01T00:00:00Z"))
    }

    private fun setField(target: Any, name: String, value: Any?) {
        var current: Class<*>? = target::class.java
        while (current != null) {
            try {
                val field = current.getDeclaredField(name)
                field.isAccessible = true
                field.set(target, value)
                return
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        error("Field $name not found on ${target::class.java.name}")
    }
}
