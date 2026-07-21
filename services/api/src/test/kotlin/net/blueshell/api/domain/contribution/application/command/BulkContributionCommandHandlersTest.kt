package net.blueshell.api.domain.contribution.application.command

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.command.BulkContributionOperation
import net.blueshell.api.domain.contribution.command.ExecuteBulkContributionCommand
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.MemberType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

class BulkContributionCommandHandlersTest {

    private val contributionService = mock<ContributionService>()
    private val userService = mock<UserService>()
    private val membershipService = mock<MembershipService>()
    private val periodService = mock<ContributionPeriodService>()

    private val handler = ExecuteBulkContributionHandler(
        contributionService,
        userService,
        membershipService,
        periodService,
    )

    @Test
    fun `mark-paid creates a contribution for a regular unpaid member`() {
        val userId = 1L
        val periodId = 100L
        val user = mockUser(userId, "Alice")
        val period = mockPeriod(periodId)
        val membership = mockMembership(MemberType.REGULAR)

        whenever(periodService.findById(periodId)).thenReturn(period)
        whenever(userService.existsById(userId)).thenReturn(true)
        whenever(userService.findById(userId)).thenReturn(user)
        whenever(membershipService.findByUserId(userId)).thenReturn(mutableListOf(membership))
        whenever(contributionService.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(false)

        val result = handler.handle(
            ExecuteBulkContributionCommand(
                userIds = listOf(userId),
                contributionPeriodId = periodId,
                operation = BulkContributionOperation.PAID,
            )
        )

        assertThat(result.applied).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(0)
        verify(contributionService).create(any())
    }

    @Test
    fun `mark-paid skips a honorary member and creates no contribution`() {
        val userId = 2L
        val periodId = 100L
        val user = mockUser(userId, "Bob")
        val period = mockPeriod(periodId)
        val honorary = mockMembership(MemberType.HONORARY)

        whenever(periodService.findById(periodId)).thenReturn(period)
        whenever(userService.existsById(userId)).thenReturn(true)
        whenever(userService.findById(userId)).thenReturn(user)
        whenever(membershipService.findByUserId(userId)).thenReturn(mutableListOf(honorary))

        val result = handler.handle(
            ExecuteBulkContributionCommand(
                userIds = listOf(userId),
                contributionPeriodId = periodId,
                operation = BulkContributionOperation.PAID,
            )
        )

        assertThat(result.applied).isEqualTo(0)
        assertThat(result.skipped).isEqualTo(1)
        verify(contributionService, never()).create(any())
    }

    @Test
    fun `mark-paid skips an unknown user id without aborting the batch`() {
        val validId = 1L
        val unknownId = 999999L
        val periodId = 100L
        val user = mockUser(validId, "Alice")
        val period = mockPeriod(periodId)
        val membership = mockMembership(MemberType.REGULAR)

        whenever(periodService.findById(periodId)).thenReturn(period)
        whenever(userService.existsById(validId)).thenReturn(true)
        whenever(userService.existsById(unknownId)).thenReturn(false)
        whenever(userService.findById(validId)).thenReturn(user)
        whenever(membershipService.findByUserId(validId)).thenReturn(mutableListOf(membership))
        whenever(contributionService.existsByUserIdAndPeriodId(validId, periodId)).thenReturn(false)

        val result = handler.handle(
            ExecuteBulkContributionCommand(
                userIds = listOf(validId, unknownId),
                contributionPeriodId = periodId,
                operation = BulkContributionOperation.PAID,
            )
        )

        assertThat(result.applied).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(1)
    }

    private fun mockUser(id: Long, name: String): User = User(
        username = "user$id",
        email = "user$id@example.com",
        password = "hash",
        initials = name.take(1).uppercase(),
        firstName = name,
        lastName = "",
    ).apply { setField(this, "id", id) }

    private fun mockPeriod(id: Long): ContributionPeriod = ContributionPeriod(
        startDate = LocalDate.of(2024, 1, 1),
        endDate = LocalDate.of(2024, 12, 31),
        halfYearFee = 50.0,
        fullYearFee = 100.0,
        alumniFee = 25.0,
    ).apply { setField(this, "id", id) }

    private fun mockMembership(memberType: MemberType): Membership = Membership(
        user = mock(),
        startDate = LocalDate.of(2023, 1, 1),
        endDate = null,
        memberType = memberType,
        incasso = false,
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
