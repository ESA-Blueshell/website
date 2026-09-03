package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.platform.config.BankProperties
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDate

/**
 * The first ask. The one payment email sent before anything is owed, which is what lets it
 * offer a direct debit mandate as a way to pay rather than as an arrangement for later.
 */
class JoiningContributionEmailBuilderTest {

    private val bank = BankProperties(
        iban = "NL01 TEST 0000 0000 00",
        bic = "TESTNL2A",
        accountName = "Blueshell Test Account",
    )
    private val channels = PaymentChannels(bank, FRONTEND_URL)
    private val member = User(
        username = "newcomer",
        email = "newcomer@example.com",
        password = "dummy",
        initials = "NC",
        firstName = "New",
        lastName = "Comer",
        phoneNumber = "0612345678",
        discord = "newcomer#0001",
    )
    private val period = ContributionPeriod(
        startDate = LocalDate.of(2025, 9, 1),
        endDate = LocalDate.of(2026, 8, 31),
        halfYearCutoffDate = LocalDate.of(2026, 2, 1),
        halfYearFee = 12.50,
        fullYearFee = 20.0,
        alumniFee = 10.0,
    )

    private fun email(
        feeType: BulkFeeType = BulkFeeType.FULL_YEAR_FEE,
        amount: Double = 20.0,
        dueDate: LocalDate = LocalDate.of(2025, 10, 15),
    ) = createJoiningContributionEmail(member, period, feeType, amount, dueDate, channels)

    @Test
    fun `welcomes the member and states what they owe, why, and by when`() {
        val content = email()

        assertThat(content.recipientEmail).isEqualTo(member.email)
        assertThat(content.subject).isEqualTo("Welcome to Blueshell Esports")
        assertThat(content.senderNameOverride).isEqualTo(SIGN_OFF)
        assertThat(content.replyToOverride).isEqualTo(REPLY_TO)
        assertThat(content.markdownContent)
            .contains("Dear New,")
            .contains("2025/2026")
            .contains("Amount due: €20,00")
            .contains(feeReason(BulkFeeType.FULL_YEAR_FEE))
            .contains("15 October 2025")
    }

    // The consequence is the reminder's, so the two emails do not describe different stakes.
    @Test
    fun `warns that the membership role is revoked if the fee is not paid`() {
        assertThat(email().markdownContent)
            .contains("membership role in our Discord and on the website is revoked")
    }

    @ParameterizedTest
    @EnumSource(BulkFeeType::class)
    fun `never quotes an amount without the reason for it`(feeType: BulkFeeType) {
        assertThat(email(feeType = feeType, amount = 12.34).markdownContent).contains(feeReason(feeType))
    }

    @Test
    fun `states all three ways the association is paid`() {
        assertThat(email().markdownContent)
            .contains(bank.iban)
            .contains(bank.bic)
            .contains(bank.accountName)
            .contains("postbus 49")
            .contains("$FRONTEND_URL/documents")
    }

    // Nothing is owed yet, so unlike on a reminder a mandate really does settle this ask.
    @Test
    fun `offers the mandate as a way to pay this fee, not as an arrangement for later`() {
        assertThat(email().markdownContent)
            .contains("you do not need to transfer anything yourself")
            .doesNotContain("from next year onwards")
    }

    private companion object {
        const val FRONTEND_URL = "https://test-frontend.com"
    }
}
