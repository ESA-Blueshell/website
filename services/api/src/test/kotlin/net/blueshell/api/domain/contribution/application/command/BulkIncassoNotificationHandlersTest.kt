package net.blueshell.api.domain.contribution.application.command

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.application.IncassoNotificationService
import net.blueshell.api.domain.contribution.command.ExecuteBulkIncassoNotificationCommand
import net.blueshell.api.domain.contribution.command.PreviewBulkIncassoNotificationCommand
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.persistence.IncassoNotification
import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.dto.bulk.BulkActionType
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.enums.MemberType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

class BulkIncassoNotificationHandlersTest {

    private val userService = mock<UserService>()
    private val membershipService = mock<MembershipService>()
    private val periodService = mock<ContributionPeriodService>()
    private val contributionService = mock<ContributionService>()
    private val notificationService = mock<IncassoNotificationService>()

    @Nested
    inner class PreviewBulkIncassoNotification {

        private val handler = PreviewBulkIncassoNotificationHandler(
            userService,
            membershipService,
            periodService,
            contributionService,
            notificationService,
        )

        @Test
        fun `preview includes users with incasso=true and no existing contribution`() {
            val userId = 1L
            val periodId = 100L
            val cutoffDate = LocalDate.of(2024, 1, 1)
            val expectedIncassoDate = LocalDate.of(2024, 2, 1)

            val user = mockUser(userId, "Alice")
            val period = mockPeriod(periodId, 50.0, 100.0, 25.0)
            val membership = mockMembership(incasso = true)

            setupMocks(user, membership, period, userId, periodId, false, null)

            val result = handler.handle(
                PreviewBulkIncassoNotificationCommand(
                    userIds = listOf(userId),
                    contributionPeriodId = periodId,
                    cutoffDate = cutoffDate,
                    expectedIncassoDate = expectedIncassoDate,
                )
            )

            assertThat(result.action).isEqualTo(BulkActionType.INCASSO_NOTIFICATION)
            assertThat(result.rows).hasSize(1)
            assertThat(result.rows[0].userId).isEqualTo(userId)
            assertThat(result.rows[0].disposition).isEqualTo(BulkRowDisposition.INCLUDED)
            assertThat(result.rows[0].reason).isNull()
            assertThat(result.rows[0].amount).isEqualTo(100.0)
            // Regular member with startDate 2023-01-01 < cutoffDate 2024-01-01 → FULL_YEAR_FEE
            assertThat(result.rows[0].recommendedFeeType).isEqualTo(BulkFeeType.FULL_YEAR_FEE)
        }

        @Test
        fun `preview excludes honorary members`() {
            val userId = 2L
            val periodId = 100L
            val cutoffDate = LocalDate.of(2024, 1, 1)
            val expectedIncassoDate = LocalDate.of(2024, 2, 1)

            val user = mockUser(userId, "Bob")
            val period = mockPeriod(periodId, 50.0, 100.0, 25.0)
            val membership = mockMembership(memberType = MemberType.HONORARY)

            setupMocks(user, membership, period, userId, periodId, false, null)

            val result = handler.handle(
                PreviewBulkIncassoNotificationCommand(
                    userIds = listOf(userId),
                    contributionPeriodId = periodId,
                    cutoffDate = cutoffDate,
                    expectedIncassoDate = expectedIncassoDate,
                )
            )

            assertThat(result.rows).hasSize(1)
            assertThat(result.rows[0].disposition).isEqualTo(BulkRowDisposition.EXCLUDED)
            assertThat(result.rows[0].reason).isEqualTo(BulkRowReason.HONORARY)
            assertThat(result.rows[0].amount).isNull()
        }

        @Test
        fun `preview warns for users without incasso=true`() {
            val userId = 3L
            val periodId = 100L
            val cutoffDate = LocalDate.of(2024, 1, 1)
            val expectedIncassoDate = LocalDate.of(2024, 2, 1)

            val user = mockUser(userId, "Charlie")
            val period = mockPeriod(periodId, 50.0, 100.0, 25.0)
            val membership = mockMembership(incasso = false)

            setupMocks(user, membership, period, userId, periodId, false, null)

            val result = handler.handle(
                PreviewBulkIncassoNotificationCommand(
                    userIds = listOf(userId),
                    contributionPeriodId = periodId,
                    cutoffDate = cutoffDate,
                    expectedIncassoDate = expectedIncassoDate,
                )
            )

            assertThat(result.rows).hasSize(1)
            assertThat(result.rows[0].disposition).isEqualTo(BulkRowDisposition.WARNING)
            assertThat(result.rows[0].reason).isEqualTo(BulkRowReason.INCASSO_MISMATCH)
            assertThat(result.rows[0].amount).isEqualTo(100.0)
        }

        @Test
        fun `preview warns for users who already paid`() {
            val userId = 4L
            val periodId = 100L
            val cutoffDate = LocalDate.of(2024, 1, 1)
            val expectedIncassoDate = LocalDate.of(2024, 2, 1)

            val user = mockUser(userId, "Diana")
            val period = mockPeriod(periodId, 50.0, 100.0, 25.0)
            val membership = mockMembership(incasso = true)

            setupMocks(user, membership, period, userId, periodId, true, null)

            val result = handler.handle(
                PreviewBulkIncassoNotificationCommand(
                    userIds = listOf(userId),
                    contributionPeriodId = periodId,
                    cutoffDate = cutoffDate,
                    expectedIncassoDate = expectedIncassoDate,
                )
            )

            assertThat(result.rows).hasSize(1)
            assertThat(result.rows[0].disposition).isEqualTo(BulkRowDisposition.WARNING)
            assertThat(result.rows[0].reason).isEqualTo(BulkRowReason.ALREADY_PAID)
            assertThat(result.rows[0].amount).isEqualTo(100.0)
        }

        private fun setupMocks(
            user: User,
            membership: Membership,
            period: ContributionPeriod,
            userId: Long,
            periodId: Long,
            alreadyPaid: Boolean,
            lastSent: Instant?
        ) {
            whenever(userService.findById(userId)).thenReturn(user)
            whenever(membershipService.findByUserId(userId)).thenReturn(mutableListOf(membership))
            whenever(periodService.findById(periodId)).thenReturn(period)
            whenever(contributionService.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(alreadyPaid)
            whenever(notificationService.findLastNotificationForUserAndPeriod(userId, periodId)).thenReturn(null)
        }
    }

    @Nested
    inner class ExecuteBulkIncassoNotification {

        private val handler = ExecuteBulkIncassoNotificationHandler(
            userService,
            membershipService,
            periodService,
            contributionService,
            notificationService,
        )

        @Test
        fun `execute creates notification for included user`() {
            val userId = 1L
            val periodId = 100L
            val cutoffDate = LocalDate.of(2024, 1, 1)
            val expectedIncassoDate = LocalDate.of(2024, 2, 1)

            val user = mockUser(userId, "Alice", email = "alice@example.com")
            val period = mockPeriod(periodId, 50.0, 100.0, 25.0)
            val membership = mockMembership(incasso = true)

            whenever(userService.findById(userId)).thenReturn(user)
            whenever(membershipService.findByUserId(userId)).thenReturn(mutableListOf(membership))
            whenever(periodService.findById(periodId)).thenReturn(period)
            whenever(contributionService.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(false)

            val capturedNotification = argumentCaptor<IncassoNotification>()
            val savedNotification = mockNotification(userId, periodId, 100.0, expectedIncassoDate)
            whenever(notificationService.create(capturedNotification.capture())).thenReturn(savedNotification)

            val result = handler.handle(
                ExecuteBulkIncassoNotificationCommand(
                    userIds = listOf(userId),
                    contributionPeriodId = periodId,
                    cutoffDate = cutoffDate,
                    expectedIncassoDate = expectedIncassoDate,
                )
            )

            assertThat(result.applied).isEqualTo(1)
            assertThat(result.skipped).isEqualTo(0)
            assertThat(result.queued).isEqualTo(1)

            assertThat(capturedNotification.firstValue.user).isSameAs(user)
            assertThat(capturedNotification.firstValue.contributionPeriod).isSameAs(period)
            assertThat(capturedNotification.firstValue.amount).isEqualTo(100.0)
            assertThat(capturedNotification.firstValue.expectedIncassoDate).isEqualTo(expectedIncassoDate)

            verify(notificationService).sendNotification(savedNotification)
        }

        @Test
        fun `execute skips honorary members`() {
            val userId = 2L
            val periodId = 100L
            val cutoffDate = LocalDate.of(2024, 1, 1)
            val expectedIncassoDate = LocalDate.of(2024, 2, 1)

            val user = mockUser(userId, "Bob")
            val period = mockPeriod(periodId, 50.0, 100.0, 25.0)
            val membership = mockMembership(memberType = MemberType.HONORARY)

            whenever(userService.findById(userId)).thenReturn(user)
            whenever(membershipService.findByUserId(userId)).thenReturn(mutableListOf(membership))
            whenever(periodService.findById(periodId)).thenReturn(period)
            whenever(contributionService.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(false)

            val result = handler.handle(
                ExecuteBulkIncassoNotificationCommand(
                    userIds = listOf(userId),
                    contributionPeriodId = periodId,
                    cutoffDate = cutoffDate,
                    expectedIncassoDate = expectedIncassoDate,
                )
            )

            assertThat(result.applied).isEqualTo(0)
            assertThat(result.skipped).isEqualTo(1)
            assertThat(result.queued).isEqualTo(0)
        }

        @Test
        fun `execute skips users without incasso by default, re-includes via includedUserIds`() {
            val userId = 3L
            val periodId = 100L
            val cutoffDate = LocalDate.of(2024, 1, 1)
            val expectedIncassoDate = LocalDate.of(2024, 2, 1)

            val user = mockUser(userId, "Charlie", email = "charlie@example.com")
            val period = mockPeriod(periodId, 50.0, 100.0, 25.0)
            val membership = mockMembership(incasso = false)

            whenever(userService.findById(userId)).thenReturn(user)
            whenever(membershipService.findByUserId(userId)).thenReturn(mutableListOf(membership))
            whenever(periodService.findById(periodId)).thenReturn(period)
            whenever(contributionService.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(false)

            val capturedNotification = argumentCaptor<IncassoNotification>()
            val savedNotification = mockNotification(userId, periodId, 100.0, expectedIncassoDate)
            whenever(notificationService.create(capturedNotification.capture())).thenReturn(savedNotification)

            val result = handler.handle(
                ExecuteBulkIncassoNotificationCommand(
                    userIds = listOf(userId),
                    contributionPeriodId = periodId,
                    cutoffDate = cutoffDate,
                    expectedIncassoDate = expectedIncassoDate,
                    includedUserIds = setOf(userId),
                )
            )

            assertThat(result.applied).isEqualTo(1)
            assertThat(result.skipped).isEqualTo(0)
            assertThat(result.queued).isEqualTo(1)
            verify(notificationService).sendNotification(savedNotification)
        }

        @Test
        fun `execute applies fee type override`() {
            val userId = 4L
            val periodId = 100L
            val cutoffDate = LocalDate.of(2024, 1, 1)
            val expectedIncassoDate = LocalDate.of(2024, 2, 1)
            // Regular member with incasso=true, default would be FULL_YEAR_FEE (100.0)
            // Override to HALF_YEAR_FEE (50.0)

            val user = mockUser(userId, "Diana", email = "diana@example.com")
            val period = mockPeriod(periodId, 50.0, 100.0, 25.0)
            val membership = mockMembership(incasso = true)

            whenever(userService.findById(userId)).thenReturn(user)
            whenever(membershipService.findByUserId(userId)).thenReturn(mutableListOf(membership))
            whenever(periodService.findById(periodId)).thenReturn(period)
            whenever(contributionService.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(false)

            val capturedNotification = argumentCaptor<IncassoNotification>()
            val savedNotification = mockNotification(userId, periodId, 50.0, expectedIncassoDate)
            whenever(notificationService.create(capturedNotification.capture())).thenReturn(savedNotification)

            val result = handler.handle(
                ExecuteBulkIncassoNotificationCommand(
                    userIds = listOf(userId),
                    contributionPeriodId = periodId,
                    cutoffDate = cutoffDate,
                    expectedIncassoDate = expectedIncassoDate,
                    feeTypeOverrides = mapOf(userId to BulkFeeType.HALF_YEAR_FEE),
                )
            )

            assertThat(result.applied).isEqualTo(1)
            // Half-year fee is 50.0 (from mockPeriod)
            assertThat(capturedNotification.firstValue.amount).isEqualTo(50.0)
        }
    }

    private fun mockUser(id: Long, name: String, email: String = "user@example.com"): User = User(
        username = "user$id",
        email = email,
        password = "hash",
        initials = name.take(1).uppercase(),
        firstName = name,
        lastName = "",
    ).apply {
        setField(this, "id", id)
    }

    private fun mockPeriod(
        id: Long,
        halfFee: Double,
        fullFee: Double,
        alumniFee: Double
    ): ContributionPeriod = ContributionPeriod(
        startDate = LocalDate.of(2024, 1, 1),
        endDate = LocalDate.of(2024, 12, 31),
        halfYearFee = halfFee,
        fullYearFee = fullFee,
        alumniFee = alumniFee,
    ).apply {
        setField(this, "id", id)
    }

    private fun mockMembership(
        memberType: MemberType = MemberType.REGULAR,
        incasso: Boolean = true
    ): Membership = Membership(
        user = mock(),
        startDate = LocalDate.of(2023, 1, 1),
        endDate = null,
        memberType = memberType,
        incasso = incasso,
    ).apply {
        setField(this, "createdAt", Instant.parse("2024-01-01T00:00:00Z"))
        setField(this, "updatedAt", Instant.parse("2024-01-01T00:00:00Z"))
    }

    private fun mockNotification(
        userId: Long,
        periodId: Long,
        amount: Double,
        expectedIncassoDate: LocalDate
    ): IncassoNotification = IncassoNotification(
        id = IncassoNotification.Id(userId, periodId),
        user = mock(),
        contributionPeriod = mock(),
        amount = amount,
        expectedIncassoDate = expectedIncassoDate,
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
