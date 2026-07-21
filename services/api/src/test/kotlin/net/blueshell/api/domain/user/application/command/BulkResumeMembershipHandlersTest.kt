package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.command.ExecuteBulkResumeMembershipCommand
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
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
            whenever(userService.existsById(userId)).thenReturn(true)
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
            whenever(userService.existsById(userId)).thenReturn(true)
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
            whenever(userService.existsById(userId)).thenReturn(true)
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
            whenever(userService.existsById(userId)).thenReturn(true)
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
        fun `skips an unknown user id without aborting the batch`() {
            val validId = 6L
            val unknownId = 999999L
            val user = mockUser(validId, "Eve")
            // Valid user has no prior membership -> StartNew branch (which calls findById).
            whenever(userService.existsById(validId)).thenReturn(true)
            whenever(userService.existsById(unknownId)).thenReturn(false)
            whenever(userService.findById(validId)).thenReturn(user)
            whenever(membershipService.findByUserId(validId)).thenReturn(mutableListOf())
            whenever(periodService.findLatest()).thenReturn(basisPeriod())

            val result = handler.handle(ExecuteBulkResumeMembershipCommand(listOf(validId, unknownId)))

            assertThat(result.applied).isEqualTo(1)
            assertThat(result.skipped).isEqualTo(1)
            verify(membershipService).create(org.mockito.kotlin.any())
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
