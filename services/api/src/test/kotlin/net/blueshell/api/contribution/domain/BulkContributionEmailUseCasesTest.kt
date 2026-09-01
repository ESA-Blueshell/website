package net.blueshell.api.contribution.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.contribution.persistence.IncassoNotification
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.dto.bulk.BulkFieldRejected
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.dto.bulk.BulkSelectionRejected
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** What a payment-email send does with the plan it is handed. */
class BulkContributionEmailUseCasesTest {

    private val periodId = 7L
    private val dueDate = LocalDate.of(2026, 3, 1)
    private val debitDate = LocalDate.of(2026, 3, 15)

    private val period = ContributionPeriod(
        startDate = LocalDate.of(2025, 9, 1),
        endDate = LocalDate.of(2026, 8, 31),
        halfYearCutoffDate = LocalDate.of(2026, 2, 1),
        halfYearFee = 25.0,
        fullYearFee = 45.0,
        alumniFee = 10.0,
    ).seeded(periodId)

    private val planner: ContributionEmailPlanner = mockk()
    private val periods: ContributionPeriodService = mockk()
    private val users: UserService = mockk()
    private val reminders: ContributionReminderService = mockk(relaxed = true)
    private val preNotifications: IncassoNotificationService = mockk(relaxed = true)

    private val useCases =
        BulkContributionEmailUseCases(planner, periods, users, reminders, preNotifications)

    @Nested
    inner class OneConfirmationSendsBoth {

        @Test
        fun `each statement is written and reported separately`() {
            plan(
                row(1L, "Ann Debit", ContributionEmailKind.INCASSO_NOTIFICATION),
                row(2L, "Ben Transfer", ContributionEmailKind.REMINDER),
                row(3L, "Cara Transfer", ContributionEmailKind.REMINDER),
            )

            val result = send()

            assertThat(result.remindersSent).isEqualTo(2)
            assertThat(result.incassoNotificationsSent).isEqualTo(1)
            assertThat(result.notWrittenTo).isEqualTo(0)
        }

        @Test
        fun `a payment request records what it asked for and when`() {
            plan(row(1L, "Ben Transfer", ContributionEmailKind.REMINDER))
            val written = slot<ContributionReminder>()
            every { reminders.create(capture(written)) } answers { written.captured }

            send()

            assertThat(written.captured.feeType).isEqualTo(BulkFeeType.FULL_YEAR_FEE)
            assertThat(written.captured.amount).isEqualTo(45.0)
            assertThat(written.captured.paymentDueDate).isEqualTo(dueDate)
        }

        @Test
        fun `a pre-notification records what will be taken and when`() {
            plan(row(1L, "Ann Debit", ContributionEmailKind.INCASSO_NOTIFICATION))
            val written = slot<IncassoNotification>()
            every { preNotifications.create(capture(written)) } answers { written.captured }

            send()

            assertThat(written.captured.amount).isEqualTo(45.0)
            assertThat(written.captured.debitDate).isEqualTo(debitDate)
        }

        @Test
        fun `every recipient's email is queued`() {
            plan(
                row(1L, "Ann Debit", ContributionEmailKind.INCASSO_NOTIFICATION),
                row(2L, "Ben Transfer", ContributionEmailKind.REMINDER),
            )

            send()

            verify(exactly = 1) { reminders.sendReminder(any()) }
            verify(exactly = 1) { preNotifications.sendNotification(any()) }
        }
    }

    @Nested
    inner class MovingAMemberOntoTheOtherEmail {

        @Test
        fun `a switched member gets the statement the treasurer chose`() {
            plan(row(1L, "Ann Debit", ContributionEmailKind.INCASSO_NOTIFICATION))

            val result = send(kindOverrides = mapOf(1L to ContributionEmailKind.REMINDER))

            assertThat(result.remindersSent).isEqualTo(1)
            assertThat(result.incassoNotificationsSent).isEqualTo(0)
        }

        @Test
        fun `choosing an email for somebody the send skips refuses the whole thing`() {
            plan(
                row(1L, "Ann Debit", ContributionEmailKind.INCASSO_NOTIFICATION),
                row(4L, "Dan Honorary", ContributionEmailKind.REMINDER, BulkRowDisposition.EXCLUDED, BulkRowReason.HONORARY),
            )

            assertThatThrownBy { send(kindOverrides = mapOf(4L to ContributionEmailKind.REMINDER)) }
                .isInstanceOf(BulkSelectionRejected::class.java)
                .satisfies({
                    val violation = (it as BulkSelectionRejected).violations.single()
                    assertThat(violation.code).isEqualTo(BulkSelectionRejected.NON_RECIPIENT_EMAIL_KINDS)
                    assertThat(violation.values).containsExactly(4L)
                })
            verify(exactly = 0) { reminders.create(any()) }
        }
    }

    @Nested
    inner class WhoTheSendWritesTo {

        @Test
        fun `a warned member is skipped until they are ticked back in`() {
            plan(
                row(1L, "Ann Transfer", ContributionEmailKind.REMINDER),
                row(2L, "Ben Paid", ContributionEmailKind.REMINDER, BulkRowDisposition.WARNING, BulkRowReason.ALREADY_PAID),
            )

            assertThat(send().remindersSent).isEqualTo(1)
            assertThat(send(forciblyIncluded = setOf(2L)).remindersSent).isEqualTo(2)
        }

        @Test
        fun `a hard-excluded member is counted as not written to, not as sent`() {
            plan(
                row(1L, "Ann Transfer", ContributionEmailKind.REMINDER),
                row(4L, "Dan Honorary", ContributionEmailKind.REMINDER, BulkRowDisposition.EXCLUDED, BulkRowReason.HONORARY),
            )

            val result = send()

            assertThat(result.remindersSent).isEqualTo(1)
            assertThat(result.notWrittenTo).isEqualTo(1)
        }
    }

    @Nested
    inner class WhenTheSelectionDoesNotAddUp {

        @Test
        fun `an id naming nobody is refused rather than dropped`() {
            plan(row(1L, "Ann Transfer", ContributionEmailKind.REMINDER))

            assertRefusal(
                { send(userIds = listOf(1L, 99L)) },
                BulkSelectionRejected.UNKNOWN_USERS,
                "userIds",
                99L,
            )
        }

        @Test
        fun `naming the same member twice is refused`() {
            plan(row(1L, "Ann Transfer", ContributionEmailKind.REMINDER))

            assertRefusal(
                { send(userIds = listOf(1L, 1L)) },
                BulkSelectionRejected.DUPLICATE_USERS,
                "userIds",
                1L,
            )
        }

        @Test
        fun `ticking back in somebody who is not in the selection is refused`() {
            plan(row(1L, "Ann Transfer", ContributionEmailKind.REMINDER))

            assertRefusal(
                { send(forciblyIncluded = setOf(99L)) },
                BulkSelectionRejected.UNKNOWN_FORCED,
                "forciblyIncludedUserIds",
                99L,
            )
        }

        @Test
        fun `ticking back in somebody the send still will not write to is refused`() {
            plan(
                row(1L, "Ann Transfer", ContributionEmailKind.REMINDER),
                row(4L, "Dan Honorary", ContributionEmailKind.REMINDER, BulkRowDisposition.EXCLUDED, BulkRowReason.HONORARY),
            )

            assertRefusal(
                { send(forciblyIncluded = setOf(4L)) },
                BulkSelectionRejected.NON_RECIPIENT_FORCED,
                "forciblyIncludedUserIds",
                4L,
            )
        }
    }

    @Nested
    inner class TheFeeThatIsStated {

        @Test
        fun `an overridden fee type re-prices the record from the period`() {
            plan(row(1L, "Ann Transfer", ContributionEmailKind.REMINDER))
            val written = slot<ContributionReminder>()
            every { reminders.create(capture(written)) } answers { written.captured }

            send(feeTypeOverrides = mapOf(1L to BulkFeeType.ALUMNI_FEE))

            assertThat(written.captured.feeType).isEqualTo(BulkFeeType.ALUMNI_FEE)
            assertThat(written.captured.amount).isEqualTo(10.0)
        }

        @Test
        fun `a fee type naming somebody the send skips refuses the whole thing`() {
            plan(
                row(1L, "Ann Transfer", ContributionEmailKind.REMINDER),
                row(4L, "Dan Honorary", ContributionEmailKind.REMINDER, BulkRowDisposition.EXCLUDED, BulkRowReason.HONORARY),
            )

            assertThatThrownBy { send(feeTypeOverrides = mapOf(4L to BulkFeeType.ALUMNI_FEE)) }
                .isInstanceOf(BulkSelectionRejected::class.java)
            verify(exactly = 0) { reminders.create(any()) }
        }
    }

    @Nested
    inner class TheDatesASendNeeds {

        @Test
        fun `a date nobody needs may be left out`() {
            plan(row(1L, "Ann Transfer", ContributionEmailKind.REMINDER))

            assertThat(send(debitDate = null).remindersSent).isEqualTo(1)
        }

        @Test
        fun `a payment request is refused without the date it promises`() {
            plan(row(1L, "Ann Transfer", ContributionEmailKind.REMINDER))

            assertDateRefusal(
                { send(paymentDueDate = null) },
                BulkFieldRejected.DATE_REQUIRED,
                "paymentDueDate",
            )
        }

        @Test
        fun `a pre-notification is refused without the date it announces`() {
            plan(row(1L, "Ann Debit", ContributionEmailKind.INCASSO_NOTIFICATION))

            assertDateRefusal({ send(debitDate = null) }, BulkFieldRejected.DATE_REQUIRED, "debitDate")
        }

        @Test
        fun `switching a member onto the other email makes that email's date required`() {
            plan(row(1L, "Ann Debit", ContributionEmailKind.INCASSO_NOTIFICATION))

            assertDateRefusal(
                { send(kindOverrides = mapOf(1L to ContributionEmailKind.REMINDER), paymentDueDate = null) },
                BulkFieldRejected.DATE_REQUIRED,
                "paymentDueDate",
            )
        }
    }

    @Nested
    inner class TheDatesAPeriodAllows {

        @Test
        fun `a due date before the period starts is refused`() {
            plan(row(1L, "Ann Transfer", ContributionEmailKind.REMINDER))

            assertDateRefusal(
                { send(paymentDueDate = period.startDate.minusDays(1)) },
                BulkFieldRejected.DATE_OUTSIDE_PERIOD,
                "paymentDueDate",
            )
        }

        @Test
        fun `a due date shortly after the period ends still chases the last unpaid members`() {
            plan(row(1L, "Ann Transfer", ContributionEmailKind.REMINDER))

            assertThat(send(paymentDueDate = period.endDate.plusMonths(3)).remindersSent).isEqualTo(1)
        }

        @Test
        fun `a due date further than three months past the period is refused`() {
            plan(row(1L, "Ann Transfer", ContributionEmailKind.REMINDER))

            assertDateRefusal(
                { send(paymentDueDate = period.endDate.plusMonths(3).plusDays(1)) },
                BulkFieldRejected.DATE_OUTSIDE_PERIOD,
                "paymentDueDate",
            )
        }

        @Test
        fun `a debit date outside the period is refused against its own field`() {
            plan(row(1L, "Ann Debit", ContributionEmailKind.INCASSO_NOTIFICATION))

            assertDateRefusal(
                { send(debitDate = period.endDate.plusYears(1)) },
                BulkFieldRejected.DATE_OUTSIDE_PERIOD,
                "debitDate",
            )
        }
    }

    // ── Fixture ──────────────────────────────────────────────────────────────

    /** A refusal names its code, its field and the ids at fault, and writes nothing. */
    private fun assertRefusal(send: () -> Unit, code: String, field: String, vararg values: Long) {
        assertThatThrownBy(send)
            .isInstanceOf(BulkSelectionRejected::class.java)
            .satisfies({
                val violation = (it as BulkSelectionRejected).violations.single { v -> v.code == code }
                assertThat(violation.field).isEqualTo(field)
                assertThat(violation.values).containsExactly(*values.toTypedArray())
            })
        verify(exactly = 0) { reminders.create(any()) }
        verify(exactly = 0) { preNotifications.create(any()) }
    }

    private fun assertDateRefusal(send: () -> Unit, code: String, field: String) {
        assertThatThrownBy(send)
            .isInstanceOf(BulkFieldRejected::class.java)
            .satisfies({
                val violation = (it as BulkFieldRejected).violations.single()
                assertThat(violation.code).isEqualTo(code)
                assertThat(violation.field).isEqualTo(field)
            })
        verify(exactly = 0) { reminders.create(any()) }
        verify(exactly = 0) { preNotifications.create(any()) }
    }

    private fun send(
        userIds: List<Long>? = null,
        forciblyIncluded: Set<Long> = emptySet(),
        kindOverrides: Map<Long, ContributionEmailKind> = emptyMap(),
        paymentDueDate: LocalDate? = dueDate,
        debitDate: LocalDate? = this.debitDate,
        feeTypeOverrides: Map<Long, BulkFeeType> = emptyMap(),
    ) = useCases.send(
        contributionPeriodId = periodId,
        userIds = userIds ?: planned.map { it.userId },
        forciblyIncluded = forciblyIncluded,
        kindOverrides = kindOverrides,
        paymentDueDate = paymentDueDate,
        debitDate = debitDate,
        feeTypeOverrides = feeTypeOverrides,
    )

    private var planned: List<ContributionEmailRow> = emptyList()

    private fun plan(vararg rows: ContributionEmailRow) {
        planned = rows.toList()
        every { planner.plan(periodId, any()) } returns ContributionEmailPlan(periodId, planned)
        every { periods.findById(periodId) } returns period
        every { users.findById(any()) } answers { member(firstArg()) }
        // `create` is generic, so a relaxed mock returns a stand-in of the erased type.
        every { reminders.create(any()) } answers { firstArg() }
        every { preNotifications.create(any()) } answers { firstArg() }
    }

    private fun row(
        userId: Long,
        name: String,
        defaultKind: ContributionEmailKind,
        disposition: BulkRowDisposition = BulkRowDisposition.INCLUDED,
        reason: BulkRowReason? = null,
    ) = ContributionEmailRow(
        userId = userId,
        name = name,
        memberType = MemberType.REGULAR,
        memberSince = LocalDate.of(2025, 9, 1),
        disposition = disposition,
        reason = reason,
        defaultKind = defaultKind,
        feeType = if (reason == BulkRowReason.HONORARY) null else BulkFeeType.FULL_YEAR_FEE,
        amount = if (reason == BulkRowReason.HONORARY) null else 45.0,
        lastRemindedOn = null,
        lastNotifiedOn = null,
    )

    private fun member(userId: Long) = User(
        username = "member$userId",
        email = "member$userId@example.com",
        password = "dummy",
        initials = "MM",
        firstName = "Member",
        lastName = "$userId",
        phoneNumber = "0612345678",
        discord = "member$userId#0001",
    ).seeded(userId)
}
