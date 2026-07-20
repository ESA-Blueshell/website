package net.blueshell.api.domain.contribution.application.email

import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.config.BankProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Tests for contribution reminder email builder.
 *
 * Verifies EmailContent is created correctly and instructs members to pay by
 * bank transfer to the Blueshell account (ADR-019, ADR-022).
 */
class ContributionReminderEmailBuilderTest {

    private val bank = BankProperties(
        iban = "NL19 INGB 0008 0964 62",
        bic = "INGBNL2A",
        accountName = "Blueshell E-Sports Vereniging",
    )

    @Test
    fun `bulk reminder builds correct EmailContent with amount and due date`() {
        // Given: User and contribution period spanning an academic year
        val user = createTestUser("john.doe", "john.doe@example.com", "John", "Doe")
        val period = createTestPeriod(
            startDate = LocalDate.of(2025, 9, 1),
            endDate = LocalDate.of(2026, 8, 31),
            halfYearFee = 25.0,
            fullYearFee = 45.0,
            alumniFee = 10.0
        )

        // When: Building the bulk contribution reminder email
        val emailContent = createContributionReminderEmail(
            user,
            period,
            amount = 45.0,
            paymentDueDate = LocalDate.of(2025, 10, 1),
            bank = bank,
        )

        // Then: EmailContent has correct fields and academic-year subject
        assertThat(emailContent.recipientEmail).isEqualTo(user.email)
        assertThat(emailContent.recipientName).isEqualTo(user.fullName)
        assertThat(emailContent.subject).isEqualTo("Please pay your Blueshell contribution (2025/2026)")
        assertThat(emailContent.senderNameOverride).isEqualTo("Secretary & Treasurer of ESA Blueshell")
        assertThat(emailContent.replyToOverride).isEqualTo("board@blueshell.utwente.nl")

        // And: Body instructs a bank transfer with the configured details and no website payment
        assertThat(emailContent.markdownContent)
            .contains("Dear John Doe")
            .contains("2025/2026")
            .contains("01 October 2025")
            .contains("Amount due: €45.00")
            .contains("NL19 INGB 0008 0964 62")
            .contains("INGBNL2A")
            .contains("Blueshell E-Sports Vereniging")
            .contains("Secretary & Treasurer of ESA Blueshell")
            .doesNotContain("via our")
            .doesNotContain("website")
        assertThat(emailContent.markdownContent).doesNotContain("—") // no em-dashes
    }

    @Test
    fun `single-user reminder lists fee options and asks for bank transfer`() {
        // Given: User and period
        val user = createTestUser("jane", "jane@example.com", "Jane", "Smith")
        val period = createTestPeriod(
            startDate = LocalDate.of(2025, 9, 1),
            endDate = LocalDate.of(2026, 8, 31),
            halfYearFee = 25.0,
            fullYearFee = 45.0,
            alumniFee = 10.0,
        )

        // When: Building the single-user email
        val emailContent = createContributionReminderEmail(user, period, bank)

        // Then: Email lists fee options and points to the bank account
        assertThat(emailContent.subject).isEqualTo("Please pay your Blueshell contribution (2025/2026)")
        assertThat(emailContent.markdownContent)
            .contains("Half year fee: €25.00")
            .contains("Full year fee: €45.00")
            .contains("Alumni fee: €10.00")
            .contains("NL19 INGB 0008 0964 62")
            .contains("If you have already paid, please disregard this message")
            .contains("Kind regards")
            .contains("Secretary & Treasurer of ESA Blueshell")
            .doesNotContain("website")
    }

    @Test
    fun `bulk reminder formats currency correctly`() {
        // Given: Precise decimal amount
        val user = createTestUser("test", "test@example.com", "Test", "User")
        val period = createTestPeriod()

        // When: Building email
        val emailContent = createContributionReminderEmail(
            user,
            period,
            amount = 12.50,
            paymentDueDate = LocalDate.now(),
            bank = bank,
        )

        // Then: Currency is formatted with 2 decimals
        assertThat(emailContent.markdownContent).contains("€12.50")
    }

    private fun createTestUser(username: String, email: String, firstName: String, lastName: String): User {
        return User(
            username = username,
            email = email,
            password = "dummy",
            initials = "${firstName.firstOrNull() ?: 'T'}${lastName.firstOrNull() ?: 'U'}",
            firstName = firstName,
            lastName = lastName,
            phoneNumber = "0612345678",
            discord = "$username#0001"
        )
    }

    private fun createTestPeriod(
        startDate: LocalDate = LocalDate.of(2025, 9, 1),
        endDate: LocalDate = LocalDate.of(2026, 8, 31),
        halfYearFee: Double = 25.0,
        fullYearFee: Double = 45.0,
        alumniFee: Double = 10.0
    ): ContributionPeriod {
        return ContributionPeriod(
            startDate = startDate,
            endDate = endDate,
            halfYearFee = halfYearFee,
            fullYearFee = fullYearFee,
            alumniFee = alumniFee,
        )
    }
}
