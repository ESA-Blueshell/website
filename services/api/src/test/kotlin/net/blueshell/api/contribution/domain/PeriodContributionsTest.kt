package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.ContributionReminderRepository
import net.blueshell.api.contribution.persistence.ContributionRepository
import net.blueshell.api.contribution.persistence.IncassoNotificationRepository
import net.blueshell.api.contribution.web.PeriodContributionsController
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.MembershipService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.LocalDate

class PeriodContributionsTest {
    private val periods: ContributionPeriodService = mock()
    private val memberships: MembershipService = mock()
    private val contributions: ContributionRepository = mock()
    private val reminders: ContributionReminderRepository = mock()
    private val notifications: IncassoNotificationRepository = mock()
    private val incassoRuns: IncassoRuns = mock()
    private val controller =
        PeriodContributionsController(PeriodContributions(periods, memberships, contributions, reminders, notifications, incassoRuns))

    @Test
    fun `lists the period's members with their fee, payment and last email, and groups the emails into runs`() {
        val period = Entities.period(4, startDate = LocalDate.of(2025, 9, 1))
        val bea = Entities.user(id = 1, username = "bea", firstName = "Bea", lastName = "Smit")
        val ann = Entities.user(id = 2, username = "ann", firstName = "Ann", lastName = "Vos")
        val hon = Entities.user(id = 3, username = "hon", firstName = "Hon", lastName = "Ored")
        whenever(periods.findById(4)).thenReturn(period)
        whenever(memberships.findOverlappingWithMembers(period.startDate, period.endDate)).thenReturn(
            listOf(
                Entities.membership(user = bea, startDate = LocalDate.of(2020, 1, 1)).apply { incasso = true },
                Entities.membership(user = ann, startDate = LocalDate.of(2026, 3, 2)),
                Entities.membership(user = hon).apply { memberType = MemberType.HONORARY },
            ),
        )
        val paidAt = Instant.parse("2025-10-01T10:00:00Z")
        whenever(contributions.findByIdContributionPeriodId(4)).thenReturn(
            mutableListOf(Entities.contribution(user = bea, period = period).apply { createdAt = paidAt }),
        )
        val sent = Instant.parse("2025-09-20T10:00:10Z")
        whenever(reminders.findByContributionPeriod_Id(4)).thenReturn(
            mutableListOf(
                Entities.reminder(user = ann, period = period).apply { askedAt = sent },
                Entities.reminder(user = hon, period = period).apply { askedAt = sent.plusSeconds(20) },
            ),
        )
        whenever(notifications.findByContributionPeriod_Id(4)).thenReturn(
            mutableListOf(Entities.incassoNotification(user = bea, period = period).apply { askedAt = sent.plusSeconds(3600) }),
        )

        val view = controller.findPeriodContributions(4)

        assertThat(view.members.map { it.username }).containsExactly("ann", "bea", "hon")
        val (annRow, beaRow, honRow) = view.members
        assertThat(annRow.feeType).isEqualTo(BulkFeeType.HALF_YEAR_FEE)
        assertThat(annRow.lastEmailKind).isEqualTo(ContributionEmailKind.REMINDER)
        assertThat(beaRow.paidAt).isEqualTo(paidAt)
        assertThat(beaRow.incasso).isTrue()
        assertThat(honRow.fee).isNull()
        assertThat(view.runs).containsExactly(
            PaymentEmailRun(ContributionEmailKind.INCASSO_NOTIFICATION, Instant.parse("2025-09-20T11:00:00Z"), 1),
            PaymentEmailRun(ContributionEmailKind.REMINDER, Instant.parse("2025-09-20T10:00:00Z"), 2),
        )
        val json = JsonMapper.builder().findAndAddModules().build()
        assertThat(json.writeValueAsString(view)).contains(
            "\"periodId\":4",
            "\"name\":\"Bea Smit\"",
            "\"paid\":true",
            "\"recipients\":2",
            "\"lastEmailAt\":",
            "\"fee\":",
            "\"feeType\":",
            "\"userId\":",
            "\"kind\":",
            "\"sentAt\":",
        )
    }
}
