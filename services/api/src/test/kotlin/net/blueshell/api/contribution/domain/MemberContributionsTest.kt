package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.ContributionReminderRepository
import net.blueshell.api.contribution.persistence.ContributionRepository
import net.blueshell.api.contribution.persistence.IncassoNotificationRepository
import net.blueshell.api.contribution.web.MemberContributionController
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.MembershipService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.LocalDate
import java.util.Optional

class MemberContributionsTest {
    private val periods: ContributionPeriodService = mock()
    private val memberships: MembershipService = mock()
    private val contributions: ContributionRepository = mock()
    private val reminders: ContributionReminderRepository = mock()
    private val notifications: IncassoNotificationRepository = mock()
    private val firsts: FirstContributions = mock()
    private val currentUser: CurrentUserProvider = mock()
    private val controller =
        MemberContributionController(
            MemberContributions(periods, memberships, contributions, reminders, notifications),
            firsts,
            currentUser,
        )

    @Test
    fun `lists each period the person was a member in, newest first, with the fee, the payment and the last email`() {
        val ann = Entities.user(id = 7)
        val older = Entities.period(1, startDate = LocalDate.of(2024, 9, 1))
        val newer = Entities.period(2, startDate = LocalDate.of(2025, 9, 1))
        val before = Entities.period(3, startDate = LocalDate.of(2020, 9, 1))
        whenever(periods.findAll()).thenReturn(listOf(older, newer, before))
        whenever(memberships.findByUserId(7)).thenReturn(
            mutableListOf(
                Entities.membership(user = ann, startDate = LocalDate.of(2024, 10, 1), endDate = LocalDate.of(2025, 8, 31)),
                Entities.membership(user = ann, startDate = LocalDate.of(2025, 9, 1)).apply { memberType = MemberType.ALUMNI },
            ),
        )
        val paid = Entities.contribution(user = ann, period = older).apply { createdAt = Instant.parse("2024-11-01T10:00:00Z") }
        whenever(contributions.findById(any())).thenReturn(Optional.empty())
        whenever(contributions.findById(Contribution.Id(7, 1))).thenReturn(Optional.of(paid))
        val asked = Instant.parse("2025-10-01T10:00:00Z")
        whenever(reminders.findByUser_Id(7)).thenReturn(
            listOf(Entities.reminder(user = ann, period = newer).apply { askedAt = asked.minusSeconds(60) }),
        )
        whenever(notifications.findByUser_Id(7)).thenReturn(
            listOf(Entities.incassoNotification(user = ann, period = newer).apply { askedAt = asked }),
        )

        val listed = controller.findMemberContributions(7)

        assertThat(listed.map { it.periodId }).containsExactly(2, 1)
        assertThat(listed[0]).isEqualTo(
            MemberPeriodContribution(
                periodId = 2,
                startDate = LocalDate.of(2025, 9, 1),
                endDate = LocalDate.of(2026, 8, 31),
                feeType = BulkFeeType.ALUMNI_FEE,
                fee = newer.alumniFee,
                paid = false,
                paidAt = null,
                lastEmailAt = asked,
                lastEmailKind = ContributionEmailKind.INCASSO_NOTIFICATION,
            ),
        )
        assertThat(listed[1].paid).isTrue()
        assertThat(listed[1].feeType).isEqualTo(BulkFeeType.FULL_YEAR_FEE)
        val mapper = JsonMapper.builder().findAndAddModules().build()
        val json = mapper.writeValueAsString(listed[1])
        assertThat(json).contains(
            "\"periodId\":1",
            "\"startDate\":\"2024-09-01\"",
            "\"endDate\":\"2025-08-31\"",
            "\"fee\":",
            "\"paid\":true",
            "\"paidAt\":",
            "\"lastEmailAt\":null",
            "\"lastEmailKind\":null",
        )
    }

    @Test
    fun `an honorary member owes nothing`() {
        val ann = Entities.user(id = 7)
        whenever(periods.findAll()).thenReturn(listOf(Entities.period(1)))
        whenever(memberships.findByUserId(7)).thenReturn(
            mutableListOf(Entities.membership(user = ann).apply { memberType = MemberType.HONORARY }),
        )
        whenever(contributions.findById(any())).thenReturn(Optional.empty())

        val one = controller.findMemberContributions(7).single()

        assertThat(one.feeType).isNull()
        assertThat(one.fee).isNull()
    }

    @Test
    fun `answers the reader's first contribution, nothing where none is pending, and refuses nobody signed in`() {
        val owed = FirstContribution(LocalDate.of(2026, 9, 10), 4, null, null, BulkFeeType.FULL_YEAR_FEE, 30.0)
        whenever(currentUser.currentUser()).thenReturn(CurrentUser(id = 1, roles = emptySet(), addressId = null))
        whenever(firsts.owedBy(1)).thenReturn(owed)
        assertThat(controller.findOwnFirstContribution().body).isEqualTo(owed)

        whenever(firsts.owedBy(1)).thenReturn(null)
        assertThat(controller.findOwnFirstContribution().statusCode.value()).isEqualTo(204)

        whenever(currentUser.currentUser()).thenReturn(null)
        assertThatThrownBy { controller.findOwnFirstContribution() }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `answers the reader's own periods, and refuses nobody signed in`() {
        val ann = Entities.user(id = 7)
        val period = Entities.period(1, startDate = LocalDate.of(2024, 9, 1))
        whenever(periods.findAll()).thenReturn(listOf(period))
        whenever(
            memberships.findByUserId(7),
        ).thenReturn(mutableListOf(Entities.membership(user = ann, startDate = LocalDate.of(2024, 10, 1))))
        whenever(contributions.findById(any())).thenReturn(Optional.empty())
        whenever(currentUser.currentUser()).thenReturn(CurrentUser(id = 7, roles = emptySet(), addressId = null))

        assertThat(controller.findOwnContributions()).isEqualTo(controller.findMemberContributions(7))
        assertThat(controller.findOwnContributions().single().periodId).isEqualTo(1)

        whenever(currentUser.currentUser()).thenReturn(null)
        assertThatThrownBy { controller.findOwnContributions() }.isInstanceOf(ResponseStatusException::class.java)
    }
}
