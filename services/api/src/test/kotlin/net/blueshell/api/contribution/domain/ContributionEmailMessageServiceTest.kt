package net.blueshell.api.contribution.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.email.api.EmailPreviewRenderer
import net.blueshell.api.platform.config.BankProperties
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.model.RenderedEmailPreview
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

/** Reading one member's payment email, built by the same builders the send uses. */
class ContributionEmailMessageServiceTest {

    private val periodId = 7L
    private val date = LocalDate.of(2026, 3, 1)

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
    private val renderer: EmailPreviewRenderer = mockk()

    private val service = ContributionEmailMessageService(planner, periods, users, renderer, PaymentChannels(BankProperties(), "https://blueshell.test"))

    private val alice = User(
        username = "alice",
        email = "alice@example.com",
        password = "hash",
        initials = "AR",
        firstName = "Alice",
        lastName = "Regular",
    ).seeded(1L)

    @Test
    fun `a payment request quotes the amount and asks for a transfer by the date`() {
        givenSelected(ContributionEmailKind.REMINDER, BulkFeeType.HALF_YEAR_FEE)
        val captured = capturingRenderer()

        val message = service.render(ContributionEmailKind.REMINDER, periodId, 1L, date, null)

        assertThat(message.kind).isEqualTo(ContributionEmailKind.REMINDER)
        assertThat(message.feeType).isEqualTo(BulkFeeType.HALF_YEAR_FEE)
        assertThat(captured.captured.markdownContent).contains("€25,00", "1 March 2026")
        assertThat(message.recipientEmail).isEqualTo("alice@example.com")
    }

    @Test
    fun `a pre-notification announces the debit and asks for no transfer`() {
        givenSelected(ContributionEmailKind.INCASSO_NOTIFICATION, BulkFeeType.FULL_YEAR_FEE)
        val captured = capturingRenderer()

        val message = service.render(ContributionEmailKind.INCASSO_NOTIFICATION, periodId, 1L, date, null)

        assertThat(message.kind).isEqualTo(ContributionEmailKind.INCASSO_NOTIFICATION)
        assertThat(captured.captured.markdownContent)
            .contains("collected", "€45,00")
            .doesNotContain("Bank transfer")
    }

    @Test
    fun `the kind asked for is the kind rendered, whatever the member's flag says`() {
        givenSelected(ContributionEmailKind.INCASSO_NOTIFICATION, BulkFeeType.FULL_YEAR_FEE)
        val captured = capturingRenderer()

        service.render(ContributionEmailKind.REMINDER, periodId, 1L, date, null)

        assertThat(captured.captured.markdownContent).contains("Bank transfer")
    }

    @Test
    fun `an overridden fee type is what the email quotes, with its own reason`() {
        givenSelected(ContributionEmailKind.REMINDER, BulkFeeType.FULL_YEAR_FEE)
        val captured = capturingRenderer()

        val message = service.render(ContributionEmailKind.REMINDER, periodId, 1L, date, BulkFeeType.ALUMNI_FEE)

        assertThat(message.feeType).isEqualTo(BulkFeeType.ALUMNI_FEE)
        assertThat(captured.captured.markdownContent).contains("€10,00", "alumni member")
    }

    @Test
    fun `a warned member's email can still be read`() {
        givenSelected(
            ContributionEmailKind.REMINDER,
            BulkFeeType.FULL_YEAR_FEE,
            disposition = BulkRowDisposition.WARNING,
            reason = BulkRowReason.ALREADY_PAID,
        )
        capturingRenderer()

        assertThat(service.render(ContributionEmailKind.REMINDER, periodId, 1L, date, null).subject).isNotBlank()
    }

    @Test
    fun `a hard-excluded member has no email to read`() {
        givenSelected(
            ContributionEmailKind.REMINDER,
            feeType = null,
            disposition = BulkRowDisposition.EXCLUDED,
            reason = BulkRowReason.HONORARY,
        )

        assertThatThrownBy { service.render(ContributionEmailKind.REMINDER, periodId, 1L, date, null) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("writes nothing")
    }

    @Test
    fun `a member the plan does not know is not found`() {
        every { planner.plan(periodId, listOf(1L)) } returns ContributionEmailPlan(periodId, emptyList())

        assertThatThrownBy { service.render(ContributionEmailKind.REMINDER, periodId, 1L, date, null) }
            .isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `reading an email goes through the shared renderer and writes nothing`() {
        givenSelected(ContributionEmailKind.REMINDER, BulkFeeType.FULL_YEAR_FEE)
        capturingRenderer()

        service.render(ContributionEmailKind.REMINDER, periodId, 1L, date, null)

        verify(exactly = 1) { renderer.render(any()) }
    }

    private fun capturingRenderer(): io.mockk.CapturingSlot<EmailContent> {
        val captured = slot<EmailContent>()
        every { renderer.render(capture(captured)) } answers {
            RenderedEmailPreview(captured.captured.subject, "<html>${captured.captured.markdownContent}</html>")
        }
        return captured
    }

    private fun givenSelected(
        defaultKind: ContributionEmailKind,
        feeType: BulkFeeType?,
        disposition: BulkRowDisposition = BulkRowDisposition.INCLUDED,
        reason: BulkRowReason? = null,
    ) {
        every { planner.plan(periodId, listOf(1L)) } returns ContributionEmailPlan(
            periodId,
            listOf(
                ContributionEmailRow(
                    userId = 1L,
                    name = alice.fullName,
                    memberType = MemberType.REGULAR,
                    memberSince = LocalDate.of(2025, 9, 1),
                    disposition = disposition,
                    reason = reason,
                    defaultKind = defaultKind,
                    feeType = feeType,
                    amount = feeType?.let { resolveFeeAmount(it, period) },
                    lastRemindedOn = null,
                    lastNotifiedOn = null,
                ),
            ),
        )
        every { periods.findById(periodId) } returns period
        every { users.findById(1L) } returns alice
    }
}
