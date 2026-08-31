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
import net.blueshell.api.shared.dto.bulk.FeeCycleGroup
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

/**
 * Reading one member's fee-cycle email.
 *
 * Which statement comes back is the member's own side of the partition, and it is built by
 * the same builders the send uses — which is what stops the preview and the email drifting.
 * A preview writes nothing, and these assert that absence.
 */
class FeeCycleEmailPreviewServiceTest {

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
    private val renderer: EmailPreviewRenderer = mockk()

    private val service = FeeCycleEmailPreviewService(planner, periods, users, renderer, BankProperties())

    private val alice = User(
        username = "alice",
        email = "alice@example.com",
        password = "hash",
        initials = "AR",
        firstName = "Alice",
        lastName = "Regular",
    ).seeded(1L)

    /** Echo the markdown back as the body, so the assertions read what was handed over. */
    private fun capturingRenderer(): io.mockk.CapturingSlot<EmailContent> {
        val captured = slot<EmailContent>()
        every { renderer.render(capture(captured)) } answers {
            RenderedEmailPreview(captured.captured.subject, "<html>${captured.captured.markdownContent}</html>")
        }
        return captured
    }

    @Test
    fun `a transfer member reads the payment request`() {
        givenInCycle(FeeCycleGroup.TRANSFER, BulkFeeType.HALF_YEAR_FEE)
        capturingRenderer()

        val preview = service.preview(periodId, 1L, dates, null)

        assertThat(preview.group).isEqualTo(FeeCycleGroup.TRANSFER)
        assertThat(preview.subject).isEqualTo("Please pay your Blueshell contribution (2025/2026)")
        assertThat(preview.html)
            .contains("Amount due: €25,00")
            .contains("the half-year fee")
            .contains("1 March 2026")
        assertThat(preview.recipientEmail).isEqualTo("alice@example.com")
        assertThat(preview.recipientName).isEqualTo("Alice Regular")
    }

    @Test
    fun `a direct-debit member reads the pre-notification`() {
        givenInCycle(FeeCycleGroup.DIRECT_DEBIT, BulkFeeType.FULL_YEAR_FEE)
        capturingRenderer()

        val preview = service.preview(periodId, 1L, dates, null)

        assertThat(preview.group).isEqualTo(FeeCycleGroup.DIRECT_DEBIT)
        assertThat(preview.subject)
            .isEqualTo("Your Blueshell contribution will be collected automatically (2025/2026)")
        assertThat(preview.html)
            .contains("Amount to be collected: €45,00")
            .contains("the full-year fee")
            .contains("15 March 2026")
    }

    // The read has to show what the send would do, overrides included.
    @Test
    fun `an overridden fee type is what the preview quotes`() {
        givenInCycle(FeeCycleGroup.TRANSFER, BulkFeeType.FULL_YEAR_FEE)
        capturingRenderer()

        val preview = service.preview(periodId, 1L, dates, BulkFeeType.ALUMNI_FEE)

        assertThat(preview.feeType).isEqualTo(BulkFeeType.ALUMNI_FEE)
        assertThat(preview.html).contains("Amount due: €10,00").contains("the alumni fee")
    }

    /**
     * The email is rendered by the shared renderer from an `EmailContent`, and nowhere else.
     * A second rendering path is how a preview starts disagreeing with what goes out.
     */
    @Test
    fun `renders through the shared renderer, from the content the send would build`() {
        givenInCycle(FeeCycleGroup.TRANSFER, BulkFeeType.FULL_YEAR_FEE)
        val handedOver = capturingRenderer()

        val preview = service.preview(periodId, 1L, dates, null)

        verify(exactly = 1) { renderer.render(any()) }
        assertThat(handedOver.captured.recipientEmail).isEqualTo("alice@example.com")
        assertThat(preview.html).isEqualTo("<html>${handedOver.captured.markdownContent}</html>")
    }

    @Test
    fun `refuses a member the cycle is not about`() {
        every { planner.plan(periodId) } returns FeeCyclePlan(periodId, emptyList())

        assertThatThrownBy { service.preview(periodId, 99L, dates, null) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("not in this period's fee cycle")
    }

    /**
     * Refused in the api rather than hidden by the dialog: an excluded member would otherwise
     * render an honorary member's statement, or one addressed to nobody.
     */
    @Test
    fun `refuses a member with no email address`() {
        every { planner.plan(periodId) } returns FeeCyclePlan(
            periodId,
            listOf(
                participant(
                    disposition = BulkRowDisposition.EXCLUDED,
                    reason = BulkRowReason.NO_EMAIL,
                    feeType = BulkFeeType.FULL_YEAR_FEE,
                ),
            ),
        )

        assertThatThrownBy { service.preview(periodId, 1L, dates, null) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("sends nothing to that member")
    }

    @Test
    fun `refuses an honorary member, who owes nothing`() {
        every { planner.plan(periodId) } returns FeeCyclePlan(
            periodId,
            listOf(
                participant(
                    disposition = BulkRowDisposition.EXCLUDED,
                    reason = BulkRowReason.HONORARY,
                    feeType = null,
                ),
            ),
        )

        assertThatThrownBy { service.preview(periodId, 1L, dates, null) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("sends nothing to that member")
    }

    private fun givenInCycle(group: FeeCycleGroup, feeType: BulkFeeType) {
        every { planner.plan(periodId) } returns FeeCyclePlan(periodId, listOf(participant(group, feeType = feeType)))
        every { users.findById(1L) } returns alice
        every { periods.findById(periodId) } returns period
    }

    private fun participant(
        group: FeeCycleGroup = FeeCycleGroup.TRANSFER,
        disposition: BulkRowDisposition = BulkRowDisposition.INCLUDED,
        reason: BulkRowReason? = null,
        feeType: BulkFeeType? = BulkFeeType.FULL_YEAR_FEE,
    ) = FeeCycleParticipant(
        userId = 1L,
        name = "Alice Regular",
        memberType = if (reason == BulkRowReason.HONORARY) MemberType.HONORARY else MemberType.REGULAR,
        memberSince = LocalDate.of(2025, 9, 1),
        group = group,
        disposition = disposition,
        reason = reason,
        feeType = feeType,
        amount = feeType?.let { resolveFeeAmount(it, period) },
        lastAskedOn = null,
    )
}
