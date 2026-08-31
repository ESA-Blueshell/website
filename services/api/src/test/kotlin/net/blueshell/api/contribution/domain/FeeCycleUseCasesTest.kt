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
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.dto.bulk.BulkSelectionRejected
import net.blueshell.api.shared.dto.bulk.FeeCycleGroup
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * What a send does with the plan it is handed.
 *
 * Both statements go out from one confirmation, each recorded in its own table, and a fee
 * type naming somebody the cycle does not write to refuses the whole send.
 */
class FeeCycleUseCasesTest {

    private val periodId = 7L
    private val dueDate = LocalDate.of(2026, 3, 1)
    private val debitDate = LocalDate.of(2026, 3, 15)
    private val dates = FeeCycleDates(paymentDue = dueDate, debit = debitDate)

    private val period = ContributionPeriod(
        startDate = LocalDate.of(2025, 9, 1),
        endDate = LocalDate.of(2026, 8, 31),
        halfYearCutoffDate = LocalDate.of(2026, 2, 1),
        halfYearFee = 25.0,
        fullYearFee = 45.0,
        alumniFee = 10.0,
    ).seeded(periodId)

    private val planner: FeeCyclePlanner = mockk()
    private val periods: ContributionPeriodService = mockk()
    private val users: UserService = mockk()
    private val reminders: ContributionReminderService = mockk(relaxed = true)
    private val preNotifications: IncassoNotificationService = mockk(relaxed = true)

    private val useCases = FeeCycleUseCases(planner, periods, users, reminders, preNotifications)

    @Nested
    inner class OneConfirmationReachesBothSides {

        @Test
        fun `each side is written to and reported separately`() {
            plan(
                participant(1L, "Ann Debit", FeeCycleGroup.DIRECT_DEBIT),
                participant(2L, "Ben Transfer", FeeCycleGroup.TRANSFER),
                participant(3L, "Cara Transfer", FeeCycleGroup.TRANSFER),
            )

            val result = useCases.send(periodId, dates, emptyMap())

            assertThat(result.paymentRequestsQueued).isEqualTo(2)
            assertThat(result.preNotificationsQueued).isEqualTo(1)
            assertThat(result.excluded).isEqualTo(0)
        }

        @Test
        fun `the payment request records the fee type it stated and the date it asked for`() {
            plan(participant(2L, "Ben Transfer", FeeCycleGroup.TRANSFER, feeType = BulkFeeType.HALF_YEAR_FEE))
            val written = slot<ContributionReminder>()
            every { reminders.create(capture(written)) } answers { written.captured }

            useCases.send(periodId, dates, emptyMap())

            assertThat(written.captured.feeType).isEqualTo(BulkFeeType.HALF_YEAR_FEE)
            assertThat(written.captured.paymentDueDate).isEqualTo(dueDate)
        }

        @Test
        fun `the pre-notification records the fee type it stated and the debit date`() {
            plan(participant(1L, "Ann Debit", FeeCycleGroup.DIRECT_DEBIT, feeType = BulkFeeType.ALUMNI_FEE))
            val written = slot<IncassoNotification>()
            every { preNotifications.create(capture(written)) } answers { written.captured }

            useCases.send(periodId, dates, emptyMap())

            assertThat(written.captured.feeType).isEqualTo(BulkFeeType.ALUMNI_FEE)
            assertThat(written.captured.debitDate).isEqualTo(debitDate)
        }

        @Test
        fun `an excluded member is written to on neither side and reported as excluded`() {
            plan(
                participant(
                    1L,
                    "Ann Honorary",
                    FeeCycleGroup.TRANSFER,
                    disposition = BulkRowDisposition.EXCLUDED,
                    reason = BulkRowReason.HONORARY,
                    feeType = null,
                ),
            )

            val result = useCases.send(periodId, dates, emptyMap())

            assertThat(result.paymentRequestsQueued).isEqualTo(0)
            assertThat(result.preNotificationsQueued).isEqualTo(0)
            assertThat(result.excluded).isEqualTo(1)
            verify(exactly = 0) { reminders.create(any()) }
            verify(exactly = 0) { preNotifications.create(any()) }
        }
    }

    @Nested
    inner class AskingAgain {

        @Test
        fun `restates the existing record rather than adding a second one`() {
            plan(participant(2L, "Ben Transfer", FeeCycleGroup.TRANSFER))
            val existing = ContributionReminder(
                id = ContributionReminder.Id(2L, periodId),
                user = member(2L, "Ben Transfer"),
                contributionPeriod = period,
            )
            every { reminders.existsById(ContributionReminder.Id(2L, periodId)) } returns true
            every { reminders.findById(ContributionReminder.Id(2L, periodId)) } returns existing
            every { reminders.update(any()) } answers { firstArg() }

            useCases.send(periodId, dates, emptyMap())

            assertThat(existing.paymentDueDate).isEqualTo(dueDate)
            verify(exactly = 0) { reminders.create(any()) }
            verify(exactly = 1) { reminders.update(existing) }
        }
    }

    @Nested
    inner class AFeeTypeForSomebodyTheCycleDoesNotWriteTo {

        @Test
        fun `refuses the whole send and names the members at fault`() {
            plan(
                participant(2L, "Ben Transfer", FeeCycleGroup.TRANSFER),
                participant(
                    9L,
                    "Ann Honorary",
                    FeeCycleGroup.TRANSFER,
                    disposition = BulkRowDisposition.EXCLUDED,
                    reason = BulkRowReason.HONORARY,
                    feeType = null,
                ),
            )

            assertThatThrownBy {
                useCases.send(periodId, dates, mapOf(9L to BulkFeeType.ALUMNI_FEE))
            }
                .isInstanceOf(BulkSelectionRejected::class.java)
                .satisfies({ thrown ->
                    val violation = (thrown as BulkSelectionRejected).violations.single()
                    assertThat(violation.code).isEqualTo(BulkSelectionRejected.NON_RECIPIENT_FEE_TYPES)
                    assertThat(violation.values).containsExactly(9L)
                })

            verify(exactly = 0) { reminders.create(any()) }
            verify(exactly = 0) { preNotifications.create(any()) }
        }

        @Test
        fun `a fee type for a recipient is honoured over the one that applies`() {
            plan(participant(2L, "Ben Transfer", FeeCycleGroup.TRANSFER, feeType = BulkFeeType.FULL_YEAR_FEE))
            val written = slot<ContributionReminder>()
            every { reminders.create(capture(written)) } answers { written.captured }

            useCases.send(periodId, dates, mapOf(2L to BulkFeeType.ALUMNI_FEE))

            assertThat(written.captured.feeType).isEqualTo(BulkFeeType.ALUMNI_FEE)
        }
    }

    // ── Fixture ──────────────────────────────────────────────────────────────

    private fun plan(vararg participants: FeeCycleParticipant) {
        every { planner.plan(periodId) } returns FeeCyclePlan(periodId, participants.toList())
        every { periods.findById(periodId) } returns period
        participants.forEach { every { users.findById(it.userId) } returns member(it.userId, it.name) }
        every { reminders.existsById(any()) } returns false
        every { preNotifications.existsById(any()) } returns false
        // A relaxed mock answers create() with a generic stub, which the send path then
        // reads an id off. Echo the argument so the record it wrote is the record it sends.
        every { reminders.create(any()) } answers { firstArg() }
        every { preNotifications.create(any()) } answers { firstArg() }
    }

    private fun participant(
        userId: Long,
        name: String,
        group: FeeCycleGroup,
        disposition: BulkRowDisposition = BulkRowDisposition.INCLUDED,
        reason: BulkRowReason? = null,
        feeType: BulkFeeType? = BulkFeeType.FULL_YEAR_FEE,
    ) = FeeCycleParticipant(
        userId = userId,
        name = name,
        memberType = MemberType.REGULAR,
        memberSince = LocalDate.of(2025, 9, 1),
        group = group,
        disposition = disposition,
        reason = reason,
        feeType = feeType,
        amount = feeType?.let { resolveFeeAmount(it, period) },
        lastAskedOn = null,
    )

    private fun member(userId: Long, fullName: String): User {
        val (firstName, lastName) = fullName.split(" ")
        return User(
            username = "member$userId",
            email = "member$userId@example.com",
            password = "dummy",
            initials = "${firstName.first()}${lastName.first()}",
            firstName = firstName,
            lastName = lastName,
            phoneNumber = "0612345678",
            discord = "member$userId#0001",
        ).seeded(userId)
    }
}
