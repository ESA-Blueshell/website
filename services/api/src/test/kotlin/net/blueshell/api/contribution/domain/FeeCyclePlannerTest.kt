package net.blueshell.api.contribution.domain

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.api.ContributionService
import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.contribution.persistence.IncassoNotification
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.dto.bulk.FeeCycleGroup
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.user.api.MembershipService
import net.blueshell.api.user.api.UserErasureService
import net.blueshell.api.user.persistence.Membership
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Who a period's fee cycle is about, and what each of them owes.
 *
 * The population is every member of the period who has not paid, so a paid member is
 * absent rather than skipped, and the side of the partition is the member's direct-debit
 * flag rather than anything the operator chose.
 */
class FeeCyclePlannerTest {

    private val periodId = 7L
    private val cutoff = LocalDate.of(2026, 2, 1)

    private val period = ContributionPeriod(
        startDate = LocalDate.of(2025, 9, 1),
        endDate = LocalDate.of(2026, 8, 31),
        halfYearCutoffDate = cutoff,
        halfYearFee = 25.0,
        fullYearFee = 45.0,
        alumniFee = 10.0,
    ).seeded(periodId)

    private val periods: ContributionPeriodService = mockk()
    private val contributions: ContributionService = mockk()
    private val memberships: MembershipService = mockk()
    private val reminders: ContributionReminderService = mockk()
    private val preNotifications: IncassoNotificationService = mockk()
    private val erasure: UserErasureService = mockk()

    private val planner =
        FeeCyclePlanner(periods, contributions, memberships, reminders, preNotifications, erasure)

    @Nested
    inner class ThePartition {

        @Test
        fun `the direct-debit flag decides the side, not the operator`() {
            given(
                membership(1L, "Ann Debit", incasso = true),
                membership(2L, "Ben Transfer", incasso = false),
            )

            val plan = planner.plan(periodId)

            assertThat(plan.group(FeeCycleGroup.DIRECT_DEBIT).map { it.name }).containsExactly("Ann Debit")
            assertThat(plan.group(FeeCycleGroup.TRANSFER).map { it.name }).containsExactly("Ben Transfer")
        }

        @Test
        fun `both sides are recipients, so one confirmation reaches everybody`() {
            given(
                membership(1L, "Ann Debit", incasso = true),
                membership(2L, "Ben Transfer", incasso = false),
            )

            assertThat(planner.plan(periodId).recipients.map { it.name })
                .containsExactly("Ann Debit", "Ben Transfer")
        }
    }

    @Nested
    inner class WhoIsInTheCycle {

        @Test
        fun `a member who has paid is absent rather than listed as skipped`() {
            given(
                membership(1L, "Ann Paid", incasso = false),
                membership(2L, "Ben Unpaid", incasso = false),
                paid = setOf(1L),
            )

            assertThat(planner.plan(periodId).participants.map { it.name }).containsExactly("Ben Unpaid")
        }

        // Their absence from the send is visible rather than silent.
        @Test
        fun `an honorary member is listed, excluded and owes nothing`() {
            given(membership(1L, "Ann Honorary", incasso = false, memberType = MemberType.HONORARY))

            val row = planner.plan(periodId).participants.single()

            assertThat(row.disposition).isEqualTo(BulkRowDisposition.EXCLUDED)
            assertThat(row.reason).isEqualTo(BulkRowReason.HONORARY)
            assertThat(row.feeType).isNull()
            assertThat(row.amount).isNull()
            assertThat(row.willSend).isFalse()
        }

        /**
         * Deletion anonymises the address to a placeholder that would pass an is-it-blank
         * test, and it does not end the memberships — so without this the cycle would write
         * to a deleted account.
         */
        @Test
        fun `a deleted account is listed, excluded and never written to`() {
            given(membership(1L, "Ann Deleted", incasso = false), deleted = true)

            val row = planner.plan(periodId).participants.single()

            assertThat(row.disposition).isEqualTo(BulkRowDisposition.EXCLUDED)
            assertThat(row.reason).isEqualTo(BulkRowReason.DELETED)
            assertThat(row.willSend).isFalse()
        }

        @Test
        fun `a member with no email address is listed and excluded`() {
            given(membership(1L, "Ann NoEmail", incasso = false, email = ""))

            val row = planner.plan(periodId).participants.single()

            assertThat(row.disposition).isEqualTo(BulkRowDisposition.EXCLUDED)
            assertThat(row.reason).isEqualTo(BulkRowReason.NO_EMAIL)
            assertThat(row.willSend).isFalse()
        }
    }

    @Nested
    inner class WhatEachOneOwes {

        @Test
        fun `the amount follows from the fee type and the period`() {
            given(
                membership(1L, "Ann Full", incasso = false, startDate = cutoff),
                membership(2L, "Ben Half", incasso = false, startDate = cutoff.plusDays(1)),
                membership(3L, "Cara Alumni", incasso = false, memberType = MemberType.ALUMNI),
            )

            val rows = planner.plan(periodId).participants.associateBy { it.name }

            assertThat(rows.getValue("Ann Full").feeType).isEqualTo(BulkFeeType.FULL_YEAR_FEE)
            assertThat(rows.getValue("Ann Full").amount).isEqualTo(45.0)
            assertThat(rows.getValue("Ben Half").feeType).isEqualTo(BulkFeeType.HALF_YEAR_FEE)
            assertThat(rows.getValue("Ben Half").amount).isEqualTo(25.0)
            assertThat(rows.getValue("Cara Alumni").feeType).isEqualTo(BulkFeeType.ALUMNI_FEE)
            assertThat(rows.getValue("Cara Alumni").amount).isEqualTo(10.0)
        }

        /**
         * A flag on a membership that has ended is not how the member pays now, and sending
         * the wrong statement on a stale flag costs them money.
         */
        @Test
        fun `the active membership decides the side, not an older one that ended`() {
            val member = member(1L, "Ann Switched")
            given(
                Membership(
                    user = member,
                    startDate = LocalDate.of(2025, 9, 1),
                    endDate = LocalDate.of(2026, 1, 31),
                    incasso = true,
                ),
                Membership(user = member, startDate = LocalDate.of(2026, 2, 1), incasso = false),
            )

            assertThat(planner.plan(periodId).participants.single().group)
                .isEqualTo(FeeCycleGroup.TRANSFER)
        }

        @Test
        fun `an ended membership still decides when none is active, as for a closed period`() {
            val member = member(1L, "Ann Left")
            given(
                Membership(
                    user = member,
                    startDate = LocalDate.of(2025, 9, 1),
                    endDate = LocalDate.of(2026, 1, 31),
                    incasso = true,
                ),
            )

            assertThat(planner.plan(periodId).participants.single().group)
                .isEqualTo(FeeCycleGroup.DIRECT_DEBIT)
        }

        // Pricing a past period by a membership that did not exist during it would be wrong,
        // so where several are active the newest of them is judged.
        @Test
        fun `the newest overlapping membership is the one judged`() {
            val member = member(1L, "Ann Rejoined")
            given(
                Membership(user = member, startDate = LocalDate.of(2025, 9, 1), incasso = false),
                Membership(user = member, startDate = cutoff.plusDays(1), incasso = false),
            )

            val row = planner.plan(periodId).participants.single()

            assertThat(row.memberSince).isEqualTo(cutoff.plusDays(1))
            assertThat(row.feeType).isEqualTo(BulkFeeType.HALF_YEAR_FEE)
        }
    }

    @Nested
    inner class WhenTheyWereLastAsked {

        @Test
        fun `a payment request is read for the transfer side and a pre-notification for the other`() {
            val debitMember = member(1L, "Ann Debit")
            val transferMember = member(2L, "Ben Transfer")
            given(
                Membership(user = debitMember, startDate = LocalDate.of(2025, 9, 1), incasso = true),
                Membership(user = transferMember, startDate = LocalDate.of(2025, 9, 1), incasso = false),
                sentReminders = listOf(reminderFor(transferMember, LocalDate.of(2026, 3, 4))),
                sentPreNotifications = listOf(preNotificationFor(debitMember, LocalDate.of(2026, 3, 5))),
            )

            val rows = planner.plan(periodId).participants.associateBy { it.name }

            assertThat(rows.getValue("Ben Transfer").lastAskedOn).isEqualTo(LocalDate.of(2026, 3, 4))
            assertThat(rows.getValue("Ann Debit").lastAskedOn).isEqualTo(LocalDate.of(2026, 3, 5))
        }

        /**
         * A member moved onto direct debit part way through a period has been asked by
         * transfer and not yet pre-notified. Pooling the two records would claim they had
         * been, and hide the send about to happen.
         */
        @Test
        fun `a request on the other side does not count as having been asked`() {
            val moved = member(1L, "Ann Moved")
            given(
                Membership(user = moved, startDate = LocalDate.of(2025, 9, 1), incasso = true),
                sentReminders = listOf(reminderFor(moved, LocalDate.of(2026, 3, 4))),
            )

            assertThat(planner.plan(periodId).participants.single().lastAskedOn).isNull()
        }

        @Test
        fun `a member never asked shows no date`() {
            given(membership(1L, "Ann New", incasso = false))

            assertThat(planner.plan(periodId).participants.single().lastAskedOn).isNull()
        }
    }

    // ── Fixture ──────────────────────────────────────────────────────────────

    private fun given(
        vararg held: Membership,
        paid: Set<Long> = emptySet(),
        deleted: Boolean = false,
        sentReminders: List<ContributionReminder> = emptyList(),
        sentPreNotifications: List<IncassoNotification> = emptyList(),
    ) {
        every { periods.findById(periodId) } returns period
        every { contributions.findByContributionPeriodId(periodId) } returns
            paid.map { Contribution(id = Contribution.Id(it, periodId), user = member(it, "Paid $it"), contributionPeriod = period) }
                .toMutableList()
        every { memberships.findOverlappingWithMembers(period.startDate, period.endDate) } returns held.toList()
        every { reminders.findByContributionPeriodId(periodId) } returns sentReminders.toMutableList()
        every { erasure.isDeleted(any()) } returns deleted
        every { preNotifications.findByContributionPeriodId(periodId) } returns sentPreNotifications.toMutableList()
    }

    private fun membership(
        userId: Long,
        fullName: String,
        incasso: Boolean,
        memberType: MemberType = MemberType.REGULAR,
        startDate: LocalDate = LocalDate.of(2025, 9, 1),
        email: String = "member$userId@example.com",
    ) = Membership(
        user = member(userId, fullName, email),
        startDate = startDate,
        memberType = memberType,
        incasso = incasso,
    )

    private fun member(userId: Long, fullName: String, email: String = "member$userId@example.com"): User {
        val (firstName, lastName) = fullName.split(" ")
        return User(
            username = "member$userId",
            email = email,
            password = "dummy",
            initials = "${firstName.first()}${lastName.first()}",
            firstName = firstName,
            lastName = lastName,
            phoneNumber = "0612345678",
            discord = "member$userId#0001",
        ).seeded(userId)
    }

    private fun reminderFor(member: User, on: LocalDate) = ContributionReminder(
        user = member,
        contributionPeriod = period,
        askedAt = on.atStartOfDay().toInstant(ZoneOffset.UTC),
    ).seeded(member.id!!)

    private fun preNotificationFor(member: User, on: LocalDate) = IncassoNotification(
        user = member,
        contributionPeriod = period,
        feeType = BulkFeeType.FULL_YEAR_FEE,
        amount = 45.0,
        debitDate = on,
        askedAt = on.atStartOfDay().toInstant(ZoneOffset.UTC),
    ).seeded(member.id!!)
}
