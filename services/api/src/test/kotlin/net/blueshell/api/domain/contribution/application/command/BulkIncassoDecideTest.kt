package net.blueshell.api.domain.contribution.application.command

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.application.IncassoNotificationService
import net.blueshell.api.domain.contribution.command.ExecuteBulkIncassoNotificationCommand
import net.blueshell.api.domain.contribution.command.PreviewBulkIncassoNotificationCommand
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.enums.MemberType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate

/**
 * NO_EMAIL surfacing and fee-override validation for the incasso decision function.
 * Complements BulkIncassoNotificationHandlersTest (which covers the disposition matrix).
 * See docs/proposals/bulk-actions/REDESIGN.md §7.
 */
class BulkIncassoDecideTest {

    private val users = mock<UserService>()
    private val memberships = mock<MembershipService>()
    private val periods = mock<ContributionPeriodService>()
    private val contributions = mock<ContributionService>()
    private val notifications = mock<IncassoNotificationService>()

    private val periodId = 100L
    private val cutoff = LocalDate.of(2024, 1, 1)
    private val incassoDate = LocalDate.of(2024, 2, 1)

    private val previewHandler get() = PreviewBulkIncassoNotificationHandler(users, memberships, periods, contributions, notifications)
    private val executeHandler get() = ExecuteBulkIncassoNotificationHandler(users, memberships, periods, contributions, notifications)

    @Test
    fun `blank email surfaces as SKIPPED NO_EMAIL in preview`() {
        val userId = 1L
        stub(userId, email = "", incasso = true)
        val row = previewHandler.handle(
            PreviewBulkIncassoNotificationCommand(listOf(userId), periodId, cutoff, incassoDate)
        ).rows[0]
        assertThat(row.disposition).isEqualTo(BulkRowDisposition.SKIPPED)
        assertThat(row.reason).isEqualTo(BulkRowReason.NO_EMAIL)
    }

    @Test
    fun `execute rejects a fee override for a non-included user with 400`() {
        val userId = 2L
        stub(userId, email = "r@x.nl", incasso = true)
        assertThatThrownBy {
            executeHandler.handle(
                ExecuteBulkIncassoNotificationCommand(
                    userIds = listOf(userId),
                    contributionPeriodId = periodId,
                    cutoffDate = cutoff,
                    expectedIncassoDate = incassoDate,
                    includedUserIds = emptySet(),
                    feeTypeOverrides = mapOf(userId to BulkFeeType.HALF_YEAR_FEE),
                )
            )
        }.isInstanceOf(ResponseStatusException::class.java)
    }

    private fun stub(userId: Long, email: String, incasso: Boolean) {
        whenever(users.findById(userId)).thenReturn(user(userId, email))
        whenever(memberships.findByUserId(userId)).thenReturn(mutableListOf(membership(incasso)))
        whenever(periods.findById(periodId)).thenReturn(period())
        whenever(contributions.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(false)
        whenever(notifications.findLastNotificationForUserAndPeriod(userId, periodId)).thenReturn(null)
    }

    private fun user(id: Long, email: String) = User(
        username = "user$id",
        email = email,
        password = "hash",
        initials = "U",
        firstName = "User",
        lastName = "$id",
    ).apply { setField(this, "id", id) }

    private fun membership(incasso: Boolean) = Membership(
        user = mock(),
        startDate = LocalDate.of(2023, 1, 1),
        endDate = null,
        memberType = MemberType.REGULAR,
        incasso = incasso,
    ).apply {
        setField(this, "createdAt", Instant.parse("2024-01-01T00:00:00Z"))
        setField(this, "updatedAt", Instant.parse("2024-01-01T00:00:00Z"))
    }

    private fun period() = ContributionPeriod(
        startDate = LocalDate.of(2024, 1, 1),
        endDate = LocalDate.of(2024, 12, 31),
        halfYearFee = 50.0,
        fullYearFee = 100.0,
        alumniFee = 25.0,
    ).apply { setField(this, "id", periodId) }

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
