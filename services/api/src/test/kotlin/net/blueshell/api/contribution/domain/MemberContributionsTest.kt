package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.ContributionReminderRepository
import net.blueshell.api.contribution.persistence.ContributionRepository
import net.blueshell.api.contribution.persistence.IncassoNotificationRepository
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.MembershipService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

class MemberContributionsTest {
    private val periods: ContributionPeriodService = mock()
    private val memberships: MembershipService = mock()
    private val contributions: ContributionRepository = mock()
    private val reminders: ContributionReminderRepository = mock()
    private val notifications: IncassoNotificationRepository = mock()
    private val own = MemberContributions(periods, memberships, contributions, reminders, notifications)

    private val ann = Entities.user(id = 7)
    private val before = Entities.period(1, startDate = LocalDate.of(2020, 9, 1))
    private val older = Entities.period(2, startDate = LocalDate.of(2024, 9, 1)).apply { fullYearFee = 30.0 }
    private val newer = Entities.period(3, startDate = LocalDate.of(2025, 9, 1)).apply { alumniFee = 10.0 }

    @BeforeEach
    fun nothingPaidOrAsked() {
        whenever(periods.findAll()).thenReturn(listOf(older, newer, before))
        whenever(contributions.findByIdUserId(7)).thenReturn(emptyList())
        whenever(reminders.findByUser_Id(7)).thenReturn(emptyList())
        whenever(notifications.findByUser_Id(7)).thenReturn(emptyList())
    }

    @Test
    fun `lists each period a membership overlaps, newest first, with the fee of that period's membership`() {
        whenever(memberships.findByUserId(7)).thenReturn(
            mutableListOf(
                Entities.membership(user = ann, startDate = LocalDate.of(2024, 9, 1), endDate = LocalDate.of(2025, 8, 31)),
                Entities.membership(user = ann, startDate = LocalDate.of(2025, 9, 1)).apply { memberType = MemberType.ALUMNI },
            ),
        )
        val paid = Entities.contribution(user = ann, period = older).apply { createdAt = Instant.parse("2024-11-01T10:00:00Z") }
        whenever(contributions.findByIdUserId(7)).thenReturn(listOf(paid))

        val listed = own.of(7)

        assertThat(listed.held).isTrue()
        assertThat(listed.periods.map { it.periodId }).containsExactly(3, 2)
        assertThat(listed.periods[0].feeType).isEqualTo(BulkFeeType.ALUMNI_FEE)
        assertThat(listed.periods[0].fee).isEqualTo(10.0)
        assertThat(listed.periods[0].paidAt).isNull()
        assertThat(listed.periods[1].feeType).isEqualTo(BulkFeeType.FULL_YEAR_FEE)
        assertThat(listed.periods[1].fee).isEqualTo(30.0)
        assertThat(listed.periods[1].paidAt).isEqualTo(Instant.parse("2024-11-01T10:00:00Z"))
    }

    @Test
    fun `takes the fee from the membership that started last where two overlap one period`() {
        whenever(memberships.findByUserId(7)).thenReturn(
            mutableListOf(
                Entities.membership(user = ann, startDate = LocalDate.of(2025, 9, 1), endDate = LocalDate.of(2025, 10, 1)),
                Entities.membership(user = ann, startDate = LocalDate.of(2025, 11, 1)).apply { memberType = MemberType.ALUMNI },
            ),
        )

        val one = own.of(7).periods.single()

        assertThat(one.feeType).isEqualTo(BulkFeeType.ALUMNI_FEE)
    }

    @Test
    fun `names no fee for an honorary membership`() {
        whenever(memberships.findByUserId(7)).thenReturn(
            mutableListOf(Entities.membership(user = ann, startDate = LocalDate.of(2025, 9, 1)).apply { memberType = MemberType.HONORARY }),
        )

        val one = own.of(7).periods.single()

        assertThat(one.feeType).isNull()
        assertThat(one.fee).isNull()
    }

    @Test
    fun `carries the latest payment email of a period, with the date it named`() {
        whenever(memberships.findByUserId(7)).thenReturn(
            mutableListOf(Entities.membership(user = ann, startDate = LocalDate.of(2024, 9, 1))),
        )
        whenever(reminders.findByUser_Id(7)).thenReturn(
            listOf(
                Entities.reminder(user = ann, period = newer).apply {
                    askedAt = Instant.parse("2025-09-10T10:00:00Z")
                    paymentDueDate = LocalDate.of(2025, 9, 24)
                },
                Entities.reminder(user = ann, period = older).apply {
                    askedAt = Instant.parse("2025-02-01T10:00:00Z")
                    paymentDueDate = LocalDate.of(2025, 2, 15)
                },
                Entities.reminder(user = ann, period = older).apply { askedAt = Instant.parse("2025-03-01T10:00:00Z") },
            ),
        )
        whenever(notifications.findByUser_Id(7)).thenReturn(
            listOf(Entities.incassoNotification(user = ann, period = newer).apply { askedAt = Instant.parse("2025-10-01T10:00:00Z") }),
        )

        val (thisYear, lastYear) = own.of(7).periods

        assertThat(thisYear.lastEmailKind).isEqualTo(ContributionEmailKind.INCASSO_NOTIFICATION)
        assertThat(thisYear.lastEmailAt).isEqualTo(Instant.parse("2025-10-01T10:00:00Z"))
        assertThat(thisYear.dueDate).isEqualTo(LocalDate.of(2025, 10, 1))
        assertThat(lastYear.lastEmailKind).isEqualTo(ContributionEmailKind.REMINDER)
        assertThat(lastYear.lastEmailAt).isEqualTo(Instant.parse("2025-03-01T10:00:00Z"))
        assertThat(lastYear.dueDate).isNull()
    }

    @Test
    fun `says whether the latest membership pays by incasso`() {
        whenever(memberships.findByUserId(7)).thenReturn(
            mutableListOf(
                Entities.membership(user = ann, startDate = LocalDate.of(2025, 9, 1)),
                Entities.membership(user = ann, startDate = LocalDate.of(2020, 9, 1), endDate = LocalDate.of(2021, 8, 31)).apply {
                    incasso = true
                },
            ),
        )
        assertThat(own.of(7).paysByIncasso).isFalse()

        whenever(memberships.findByUserId(7)).thenReturn(
            mutableListOf(Entities.membership(user = ann, startDate = LocalDate.of(2025, 9, 1)).apply { incasso = true }),
        )
        assertThat(own.of(7).paysByIncasso).isTrue()
    }

    @Test
    fun `holds a pending membership with no period yet, and nothing for somebody who never joined`() {
        whenever(periods.findAll()).thenReturn(emptyList())
        whenever(memberships.findByUserId(7)).thenReturn(mutableListOf(Entities.membership(user = ann, activatedOn = null)))
        assertThat(own.of(7)).isEqualTo(OwnContributions(held = true, paysByIncasso = false, periods = emptyList()))

        whenever(memberships.findByUserId(7)).thenReturn(mutableListOf())
        assertThat(own.of(7)).isEqualTo(OwnContributions(held = false, paysByIncasso = false, periods = emptyList()))
    }
}
