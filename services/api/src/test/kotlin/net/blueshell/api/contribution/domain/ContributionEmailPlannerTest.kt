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
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.user.api.MembershipService
import net.blueshell.api.user.api.UserErasureService
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.Membership
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** What the payment emails would do to a selection. */
class ContributionEmailPlannerTest {

    private val periodId = 7L

    private val period = ContributionPeriod(
        startDate = LocalDate.of(2025, 9, 1),
        endDate = LocalDate.of(2026, 8, 31),
        halfYearCutoffDate = LocalDate.of(2026, 2, 1),
        halfYearFee = 25.0,
        fullYearFee = 45.0,
        alumniFee = 10.0,
    ).seeded(periodId)

    private val periods: ContributionPeriodService = mockk()
    private val contributions: ContributionService = mockk()
    private val memberships: MembershipService = mockk()
    private val users: UserService = mockk()
    private val reminders: ContributionReminderService = mockk()
    private val preNotifications: IncassoNotificationService = mockk()
    private val erasure: UserErasureService = mockk()

    private val planner = ContributionEmailPlanner(
        periods, contributions, memberships, users, reminders, preNotifications, erasure,
    )

    @Nested
    inner class WhichEmailAMemberGets {

        @Test
        fun `the direct-debit flag chooses it, not the operator`() {
            given(membership(1L, "Ann Debit", incasso = true), membership(2L, "Ben Transfer", incasso = false))

            val plan = planner.plan(periodId, listOf(1L, 2L))

            assertThat(plan.byUserId(1L)!!.defaultKind).isEqualTo(ContributionEmailKind.INCASSO_NOTIFICATION)
            assertThat(plan.byUserId(2L)!!.defaultKind).isEqualTo(ContributionEmailKind.REMINDER)
        }

        @Test
        fun `the flag is read off the membership still running, not the one that ended`() {
            val member = member(1L, "Ann Moved")
            given(
                Membership(user = member, startDate = LocalDate.of(2024, 9, 1), endDate = LocalDate.of(2025, 8, 31), incasso = true),
                Membership(user = member, startDate = LocalDate.of(2025, 9, 1), incasso = false),
            )

            assertThat(planner.plan(periodId, listOf(1L)).byUserId(1L)!!.defaultKind)
                .isEqualTo(ContributionEmailKind.REMINDER)
        }

        @Test
        fun `a member holding no membership at all is asked to transfer`() {
            givenNoMemberships(member(9L, "Kim Ada"))

            assertThat(planner.plan(periodId, listOf(9L)).byUserId(9L)!!.defaultKind)
                .isEqualTo(ContributionEmailKind.REMINDER)
        }
    }

    @Nested
    inner class WhoIsWrittenTo {

        @Test
        fun `the selection is the population, so nobody is added to it`() {
            given(membership(1L, "Ann One", incasso = false), membership(2L, "Ben Two", incasso = false))

            assertThat(planner.plan(periodId, listOf(1L)).rows.map { it.name }).containsExactly("Ann One")
        }

        @Test
        fun `an honorary member is listed, hard-excluded and owes nothing`() {
            given(membership(1L, "Ann Honorary", incasso = false, memberType = MemberType.HONORARY))

            val row = planner.plan(periodId, listOf(1L)).rows.single()

            assertThat(row.disposition).isEqualTo(BulkRowDisposition.EXCLUDED)
            assertThat(row.reason).isEqualTo(BulkRowReason.HONORARY)
            assertThat(row.feeType).isNull()
            assertThat(row.amount).isNull()
            assertThat(row.isHardExcluded).isTrue()
        }

        @Test
        fun `a deleted account is hard-excluded`() {
            given(membership(1L, "Ann Gone", incasso = false), deleted = true)

            val row = planner.plan(periodId, listOf(1L)).rows.single()

            assertThat(row.disposition).isEqualTo(BulkRowDisposition.EXCLUDED)
            assertThat(row.reason).isEqualTo(BulkRowReason.DELETED)
        }

        @Test
        fun `a member with no address on file is hard-excluded`() {
            given(membership(1L, "Ann Unreachable", incasso = false, email = ""))

            val row = planner.plan(periodId, listOf(1L)).rows.single()

            assertThat(row.disposition).isEqualTo(BulkRowDisposition.EXCLUDED)
            assertThat(row.reason).isEqualTo(BulkRowReason.NO_EMAIL)
        }

        @Test
        fun `a hard exclusion is not overridable`() {
            given(membership(1L, "Ann Honorary", incasso = false, memberType = MemberType.HONORARY))

            assertThat(planner.plan(periodId, listOf(1L)).recipients(setOf(1L))).isEmpty()
        }

        @Test
        fun `a member who has paid is warned about rather than dropped`() {
            given(membership(1L, "Ann Paid", incasso = false), paid = setOf(1L))

            val row = planner.plan(periodId, listOf(1L)).rows.single()

            assertThat(row.disposition).isEqualTo(BulkRowDisposition.WARNING)
            assertThat(row.reason).isEqualTo(BulkRowReason.ALREADY_PAID)
        }

        @Test
        fun `a member who held no membership during the period is warned about`() {
            given(
                membership(
                    1L,
                    "Kim Ada",
                    incasso = false,
                    startDate = LocalDate.of(2023, 9, 1),
                    endDate = LocalDate.of(2024, 8, 31),
                ),
            )

            val row = planner.plan(periodId, listOf(1L)).rows.single()

            assertThat(row.disposition).isEqualTo(BulkRowDisposition.WARNING)
            assertThat(row.reason).isEqualTo(BulkRowReason.NOT_MEMBER_IN_PERIOD)
        }

        @Test
        fun `a warned member is written to once ticked back in`() {
            given(membership(1L, "Ann Paid", incasso = false), paid = setOf(1L))

            val plan = planner.plan(periodId, listOf(1L))

            assertThat(plan.recipients(emptySet())).isEmpty()
            assertThat(plan.recipients(setOf(1L)).map { it.name }).containsExactly("Ann Paid")
        }

        @Test
        fun `an id naming nobody is named on the plan rather than dropped`() {
            given(membership(1L, "Ann One", incasso = false))
            every { users.existsById(99L) } returns false

            val plan = planner.plan(periodId, listOf(1L, 99L))

            assertThat(plan.rows.map { it.userId }).containsExactly(1L)
            assertThat(plan.unknownUserIds).containsExactly(99L)
        }

        @Test
        fun `a selection that resolves whole names nobody as unknown`() {
            given(membership(1L, "Ann One", incasso = false))

            assertThat(planner.plan(periodId, listOf(1L)).unknownUserIds).isEmpty()
        }
    }

    @Nested
    inner class WhatEachMemberOwes {

        @Test
        fun `a membership started before the cutoff pays the full year`() {
            given(membership(1L, "Ann Early", incasso = false, startDate = LocalDate.of(2025, 9, 1)))

            val row = planner.plan(periodId, listOf(1L)).rows.single()

            assertThat(row.feeType).isEqualTo(BulkFeeType.FULL_YEAR_FEE)
            assertThat(row.amount).isEqualTo(45.0)
        }

        @Test
        fun `a membership started after the cutoff pays the half year`() {
            given(membership(1L, "Ben Late", incasso = false, startDate = LocalDate.of(2026, 3, 1)))

            val row = planner.plan(periodId, listOf(1L)).rows.single()

            assertThat(row.feeType).isEqualTo(BulkFeeType.HALF_YEAR_FEE)
            assertThat(row.amount).isEqualTo(25.0)
        }

        @Test
        fun `an alumni member pays the alumni fee`() {
            given(membership(1L, "Cara Alumni", incasso = false, memberType = MemberType.ALUMNI))

            val row = planner.plan(periodId, listOf(1L)).rows.single()

            assertThat(row.feeType).isEqualTo(BulkFeeType.ALUMNI_FEE)
            assertThat(row.amount).isEqualTo(10.0)
        }
    }

    @Nested
    inner class WhenTheyWereLastWrittenTo {

        @Test
        fun `each statement carries its own date`() {
            val ann = member(1L, "Ann Moved")
            given(
                Membership(user = ann, startDate = LocalDate.of(2025, 9, 1), incasso = true),
                sentReminders = listOf(reminderFor(ann, LocalDate.of(2025, 9, 12))),
            )

            val row = planner.plan(periodId, listOf(1L)).rows.single()

            assertThat(row.lastRemindedOn).isEqualTo(LocalDate.of(2025, 9, 12))
            assertThat(row.lastNotifiedOn).isNull()
        }

        @Test
        fun `the most recent of several sends is the one reported`() {
            val ann = member(1L, "Ann Chased")
            given(
                Membership(user = ann, startDate = LocalDate.of(2025, 9, 1), incasso = false),
                sentReminders = listOf(
                    reminderFor(ann, LocalDate.of(2025, 9, 12)),
                    reminderFor(ann, LocalDate.of(2026, 2, 3)),
                ),
            )

            assertThat(planner.plan(periodId, listOf(1L)).rows.single().lastRemindedOn)
                .isEqualTo(LocalDate.of(2026, 2, 3))
        }

        @Test
        fun `a member never written to shows no date on either`() {
            given(membership(1L, "Ann New", incasso = false))

            val row = planner.plan(periodId, listOf(1L)).rows.single()

            assertThat(row.lastRemindedOn).isNull()
            assertThat(row.lastNotifiedOn).isNull()
        }
    }

    private fun given(
        vararg held: Membership,
        paid: Set<Long> = emptySet(),
        deleted: Boolean = false,
        sentReminders: List<ContributionReminder> = emptyList(),
        sentPreNotifications: List<IncassoNotification> = emptyList(),
    ) {
        every { periods.findById(periodId) } returns period
        every { contributions.findByContributionPeriodId(periodId) } returns
            paid.map {
                Contribution(
                    id = Contribution.Id(it, periodId),
                    user = member(it, "Paid $it"),
                    contributionPeriod = period,
                )
            }.toMutableList()
        every { memberships.findByUserIdsWithMembers(any()) } returns held.toList().groupBy { it.userId }
        every { reminders.findByContributionPeriodId(periodId) } returns sentReminders.toMutableList()
        every { preNotifications.findByContributionPeriodId(periodId) } returns sentPreNotifications.toMutableList()
        every { erasure.isDeleted(any()) } returns deleted
    }

    private fun givenNoMemberships(member: User) {
        given()
        every { users.existsById(member.id!!) } returns true
        every { users.findById(member.id!!) } returns member
    }

    private fun membership(
        userId: Long,
        fullName: String,
        incasso: Boolean,
        memberType: MemberType = MemberType.REGULAR,
        startDate: LocalDate = LocalDate.of(2025, 9, 1),
        endDate: LocalDate? = null,
        email: String = "member$userId@example.com",
    ) = Membership(
        user = member(userId, fullName, email),
        startDate = startDate,
        endDate = endDate,
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
}
