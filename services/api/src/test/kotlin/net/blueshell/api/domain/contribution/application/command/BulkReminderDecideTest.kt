package net.blueshell.api.domain.contribution.application.command

import net.blueshell.api.domain.contribution.application.ContributionPeriodService
import net.blueshell.api.domain.contribution.application.ContributionReminderService
import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.command.ExecuteBulkContributionReminderCommand
import net.blueshell.api.domain.contribution.command.PreviewBulkContributionReminderCommand
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
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
 * Boundary coverage for the contribution-reminder decision function, plus the invariant
 * that preview dispositions and execute outcomes agree (they now share decideReminder).
 * See docs/proposals/bulk-actions/REDESIGN.md §7.
 */
class BulkReminderDecideTest {

    private val users = mock<UserService>()
    private val memberships = mock<MembershipService>()
    private val periods = mock<ContributionPeriodService>()
    private val contributions = mock<ContributionService>()
    private val reminders = mock<ContributionReminderService>()

    private val periodId = 100L
    private val cutoff = LocalDate.of(2024, 1, 1)
    private val dueDate = LocalDate.of(2024, 3, 1)

    private val previewHandler get() = PreviewBulkContributionReminderHandler(users, memberships, periods, contributions, reminders)
    private val executeHandler get() = ExecuteBulkContributionReminderHandler(users, memberships, periods, contributions, reminders)

    @Test
    fun `blank email produces SKIPPED NO_EMAIL in preview and is skipped by execute`() {
        val userId = 1L
        stub(userId, email = "", memberType = MemberType.REGULAR, incassoStart = LocalDate.of(2023, 1, 1), alreadyPaid = false)

        val row = preview(userId).rows[0]
        assertThat(row.disposition).isEqualTo(BulkRowDisposition.SKIPPED)
        assertThat(row.reason).isEqualTo(BulkRowReason.NO_EMAIL)

        // Even re-including a NO_EMAIL user cannot send.
        val result = executeHandler.handle(execCommand(userId, included = setOf(userId)))
        assertThat(result.applied).isEqualTo(0)
        assertThat(result.skipped).isEqualTo(1)
        assertThat(result.queued).isEqualTo(0)
    }

    @Test
    fun `honorary is EXCLUDED and gets no fee`() {
        val userId = 2L
        stub(userId, email = "h@x.nl", memberType = MemberType.HONORARY, incassoStart = LocalDate.of(2023, 1, 1), alreadyPaid = false)

        val row = preview(userId).rows[0]
        assertThat(row.disposition).isEqualTo(BulkRowDisposition.EXCLUDED)
        assertThat(row.reason).isEqualTo(BulkRowReason.HONORARY)
        assertThat(row.recommendedFeeType).isNull()
    }

    @Test
    fun `REGULAR started on the cutoff resolves to HALF_YEAR_FEE (boundary is inclusive)`() {
        val userId = 3L
        stub(userId, email = "r@x.nl", memberType = MemberType.REGULAR, incassoStart = cutoff, alreadyPaid = false)

        val row = preview(userId).rows[0]
        assertThat(row.disposition).isEqualTo(BulkRowDisposition.INCLUDED)
        assertThat(row.recommendedFeeType).isEqualTo(BulkFeeType.HALF_YEAR_FEE)
    }

    @Test
    fun `already-paid is a WARNING that execute skips unless re-included`() {
        val userId = 4L
        stub(userId, email = "p@x.nl", memberType = MemberType.REGULAR, incassoStart = LocalDate.of(2023, 1, 1), alreadyPaid = true)

        assertThat(preview(userId).rows[0].disposition).isEqualTo(BulkRowDisposition.WARNING)

        val skipped = executeHandler.handle(execCommand(userId, included = emptySet()))
        assertThat(skipped.applied).isEqualTo(0)
        assertThat(skipped.skipped).isEqualTo(1)

        stubCreate(userId)
        val applied = executeHandler.handle(execCommand(userId, included = setOf(userId)))
        assertThat(applied.applied).isEqualTo(1)
        assertThat(applied.queued).isEqualTo(1)
    }

    @Test
    fun `execute rejects a fee override for an excluded user with 400`() {
        val userId = 5L
        stub(userId, email = "h@x.nl", memberType = MemberType.HONORARY, incassoStart = LocalDate.of(2023, 1, 1), alreadyPaid = false)

        assertThatThrownBy {
            executeHandler.handle(execCommand(userId, included = setOf(userId), overrides = mapOf(userId to BulkFeeType.FULL_YEAR_FEE)))
        }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `execute rejects a fee override for a user not in the included set with 400`() {
        val userId = 6L
        stub(userId, email = "r@x.nl", memberType = MemberType.REGULAR, incassoStart = LocalDate.of(2023, 1, 1), alreadyPaid = false)

        assertThatThrownBy {
            executeHandler.handle(execCommand(userId, included = emptySet(), overrides = mapOf(userId to BulkFeeType.HALF_YEAR_FEE)))
        }.isInstanceOf(ResponseStatusException::class.java)
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun preview(userId: Long) = previewHandler.handle(
        PreviewBulkContributionReminderCommand(listOf(userId), periodId, cutoff, dueDate)
    )

    private fun execCommand(
        userId: Long,
        included: Set<Long>,
        overrides: Map<Long, BulkFeeType> = emptyMap(),
    ) = ExecuteBulkContributionReminderCommand(listOf(userId), periodId, cutoff, dueDate, included, overrides)

    private fun stub(
        userId: Long,
        email: String,
        memberType: MemberType,
        incassoStart: LocalDate,
        alreadyPaid: Boolean,
    ) {
        whenever(users.findById(userId)).thenReturn(user(userId, email))
        whenever(memberships.findByUserId(userId)).thenReturn(mutableListOf(membership(memberType, incassoStart)))
        whenever(periods.findById(periodId)).thenReturn(period())
        whenever(contributions.existsByUserIdAndPeriodId(userId, periodId)).thenReturn(alreadyPaid)
        whenever(reminders.findLastReminderForUserAndPeriod(userId, periodId)).thenReturn(null)
    }

    private fun stubCreate(userId: Long) {
        val saved = ContributionReminder(
            id = ContributionReminder.Id(userId, periodId),
            user = mock(),
            contributionPeriod = mock(),
            amount = 100.0,
            paymentDueDate = dueDate,
        ).apply {
            setField(this, "createdAt", Instant.parse("2024-01-01T00:00:00Z"))
            setField(this, "updatedAt", Instant.parse("2024-01-01T00:00:00Z"))
        }
        whenever(reminders.create(org.mockito.kotlin.any())).thenReturn(saved)
    }

    private fun user(id: Long, email: String) = User(
        username = "user$id",
        email = email,
        password = "hash",
        initials = "U",
        firstName = "User",
        lastName = "$id",
    ).apply { setField(this, "id", id) }

    private fun membership(memberType: MemberType, start: LocalDate) = Membership(
        user = mock(),
        startDate = start,
        endDate = null,
        memberType = memberType,
        incasso = false,
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
