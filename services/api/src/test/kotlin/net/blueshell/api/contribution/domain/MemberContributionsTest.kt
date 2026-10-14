package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.ContributionReminderRepository
import net.blueshell.api.contribution.persistence.ContributionRepository
import net.blueshell.api.contribution.persistence.IncassoNotificationRepository
import net.blueshell.api.contribution.web.BankAccountResponse
import net.blueshell.api.contribution.web.MemberContributionController
import net.blueshell.api.platform.config.BankProperties
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.MembershipService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.LocalDate

class MemberContributionsTest {
    private val periods: ContributionPeriodService = mock()
    private val memberships: MembershipService = mock()
    private val contributions: ContributionRepository = mock()
    private val reminders: ContributionReminderRepository = mock()
    private val notifications: IncassoNotificationRepository = mock()
    private val currentUser: CurrentUserProvider = mock()
    private val bank = BankProperties(iban = "NL00 TEST 0000 0000 00", bic = "TESTNL2A", accountName = "Test Vereniging")
    private val controller =
        MemberContributionController(
            MemberContributions(periods, memberships, contributions, reminders, notifications),
            currentUser,
            PaymentChannels(bank, "https://site.test"),
        )

    @Test
    fun `answers the account a member transfers the contribution to`() {
        assertThat(controller.findBankAccount()).isEqualTo(BankAccountResponse("NL00 TEST 0000 0000 00", "TESTNL2A", "Test Vereniging"))
    }

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
        whenever(contributions.findByIdUserId(7)).thenReturn(listOf(paid))
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
        whenever(contributions.findByIdUserId(7)).thenReturn(emptyList())

        val one = controller.findMemberContributions(7).single()

        assertThat(one.feeType).isNull()
        assertThat(one.fee).isNull()
    }

    @Test
    fun `lists a period paid outside every membership, so the payment can be withdrawn`() {
        val ann = Entities.user(id = 7)
        val paidFor = Entities.period(1, startDate = LocalDate.of(2020, 9, 1))
        whenever(periods.findAll()).thenReturn(listOf(paidFor, Entities.period(2, startDate = LocalDate.of(2019, 9, 1))))
        whenever(memberships.findByUserId(7)).thenReturn(mutableListOf())
        val paid = Entities.contribution(user = ann, period = paidFor).apply { createdAt = Instant.parse("2020-10-01T10:00:00Z") }
        whenever(contributions.findByIdUserId(7)).thenReturn(listOf(paid))

        val one = controller.findMemberContributions(7).single()

        assertThat(one.periodId).isEqualTo(1)
        assertThat(one.paid).isTrue()
        assertThat(one.feeType).isNull()
        assertThat(one.fee).isNull()
    }

    @Test
    fun `answers the reader's own periods, and refuses nobody signed in`() {
        val ann = Entities.user(id = 7)
        val period = Entities.period(1, startDate = LocalDate.of(2024, 9, 1))
        whenever(periods.findAll()).thenReturn(listOf(period))
        whenever(
            memberships.findByUserId(7),
        ).thenReturn(mutableListOf(Entities.membership(user = ann, startDate = LocalDate.of(2024, 10, 1))))
        whenever(contributions.findByIdUserId(7)).thenReturn(emptyList())
        whenever(currentUser.currentUser()).thenReturn(CurrentUser(id = 7, roles = emptySet(), addressId = null))

        assertThat(controller.findOwnContributions()).isEqualTo(controller.findMemberContributions(7))
        assertThat(controller.findOwnContributions().single().periodId).isEqualTo(1)

        whenever(currentUser.currentUser()).thenReturn(null)
        assertThatThrownBy { controller.findOwnContributions() }.isInstanceOf(ResponseStatusException::class.java)
    }
}
