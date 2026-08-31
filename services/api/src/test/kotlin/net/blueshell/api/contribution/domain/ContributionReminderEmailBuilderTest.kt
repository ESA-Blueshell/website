package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.platform.config.BankProperties
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate
import java.util.Locale

/**
 * The two payment requests.
 *
 * The fee-cycle one quotes one amount and the reason it applies; the single-member one
 * quotes the period's options because no fee type was chosen for it.
 */
class ContributionReminderEmailBuilderTest {

    private val bank = BankProperties(
        iban = "NL01 TEST 0000 0000 00",
        bic = "TESTNL2A",
        accountName = "Blueshell Test Account",
    )
    private val originalLocale: Locale = Locale.getDefault()

    @AfterEach
    fun restoreDefaultLocale() {
        Locale.setDefault(originalLocale)
    }

    @Nested
    inner class TheFeeCyclePaymentRequest {

        @Test
        fun `states the amount, the reason for it and the date it is due`() {
            val user = createTestUser("john.doe", "john.doe@example.com", "John", "Doe")
            val period = createTestPeriod(
                startDate = LocalDate.of(2025, 9, 1),
                endDate = LocalDate.of(2026, 8, 31),
            )

            val email = createContributionReminderEmail(
                user,
                period,
                BulkFeeType.HALF_YEAR_FEE,
                LocalDate.of(2026, 3, 1),
                bank,
            )

            assertThat(email.recipientEmail).isEqualTo(user.email)
            assertThat(email.recipientName).isEqualTo(user.fullName)
            assertThat(email.subject).isEqualTo("Please pay your Blueshell contribution (2025/2026)")
            assertThat(email.replyToOverride).isEqualTo("board@blueshell.utwente.nl")
            assertThat(email.markdownContent)
                .contains("Dear John Doe")
                .contains("Amount due: €25,00")
                .contains("the half-year fee, as your membership started during the second half of the year")
                .contains("1 March 2026")
                .contains(bank.iban)
                .contains(bank.bic)
                .contains(bank.accountName)
        }

        // The reason is what stops the amount inviting a reply asking where it came from.
        @ParameterizedTest
        @EnumSource(BulkFeeType::class)
        fun `never quotes an amount without the reason for it`(feeType: BulkFeeType) {
            val email = createContributionReminderEmail(
                createTestUser("test", "test@example.com", "Test", "User"),
                createTestPeriod(),
                feeType,
                LocalDate.now().plusMonths(1),
                bank,
            )

            assertThat(email.markdownContent).contains(feeReason(feeType))
        }

        @Test
        fun `quotes the amount the fee type prices, not another one`() {
            val period = createTestPeriod(halfYearFee = 25.0, fullYearFee = 45.0, alumniFee = 10.0)
            val user = createTestUser("test", "test@example.com", "Test", "User")
            val dueDate = LocalDate.now().plusMonths(1)

            val alumni = createContributionReminderEmail(user, period, BulkFeeType.ALUMNI_FEE, dueDate, bank)

            assertThat(alumni.markdownContent)
                .contains("Amount due: €10,00")
                .doesNotContain("€45,00")
                .doesNotContain("€25,00")
        }

        @ParameterizedTest
        @ValueSource(strings = ["nl-NL", "en-US", "de-DE"])
        fun `formats currency in Dutch notation whatever the JVM default locale is`(languageTag: String) {
            Locale.setDefault(Locale.forLanguageTag(languageTag))

            val email = createContributionReminderEmail(
                createTestUser("test", "test@example.com", "Test", "User"),
                createTestPeriod(fullYearFee = 20.0),
                BulkFeeType.FULL_YEAR_FEE,
                LocalDate.now().plusMonths(1),
                bank,
            )

            assertThat(email.markdownContent).contains("€20,00")
        }
    }

    @Nested
    inner class TheSingleMemberReminder {

        @Test
        fun `lists the period's fee options, because no fee type was chosen`() {
            val user = createTestUser("jane", "jane@example.com", "Jane", "Smith")
            val period = createTestPeriod(halfYearFee = 25.0, fullYearFee = 45.0, alumniFee = 10.0)

            val email = createContributionReminderEmail(user, period, bank)

            assertThat(email.markdownContent)
                .contains("Dear Jane Smith")
                .contains("Half year fee: €25,00")
                .contains("Full year fee: €45,00")
                .contains("Alumni fee: €10,00")
                .contains("If you have already paid, please disregard this message")
                .contains("Kind regards")
                .contains("Secretary & Treasurer of ESA Blueshell")
        }

        @ParameterizedTest
        @ValueSource(strings = ["nl-NL", "en-US", "de-DE"])
        fun `formats currency in Dutch notation whatever the JVM default locale is`(languageTag: String) {
            Locale.setDefault(Locale.forLanguageTag(languageTag))

            val email = createContributionReminderEmail(
                createTestUser("test", "test@example.com", "Test", "User"),
                createTestPeriod(halfYearFee = 12.50, fullYearFee = 20.00, alumniFee = 5.99),
                bank,
            )

            assertThat(email.markdownContent)
                .contains("€12,50")
                .contains("€20,00")
                .contains("€5,99")
        }
    }

    @Nested
    inner class TheAcademicYearLabel {

        @Test
        fun `spans both years when the period does`() {
            val period = createTestPeriod(
                startDate = LocalDate.of(2025, 9, 1),
                endDate = LocalDate.of(2026, 8, 31),
            )
            assertThat(academicYearLabel(period)).isEqualTo("2025/2026")
        }

        @Test
        fun `names one year when the period sits inside one`() {
            val period = createTestPeriod(
                startDate = LocalDate.of(2025, 1, 1),
                endDate = LocalDate.of(2025, 12, 31),
            )
            assertThat(academicYearLabel(period)).isEqualTo("2025")
        }
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
        startDate: LocalDate = LocalDate.now(),
        endDate: LocalDate = LocalDate.now().plusMonths(6),
        halfYearFee: Double = 25.0,
        fullYearFee: Double = 45.0,
        alumniFee: Double = 10.0
    ): ContributionPeriod {
        return ContributionPeriod(
            startDate = startDate,
            endDate = endDate,
            halfYearCutoffDate = startDate.plusMonths(3),
            halfYearFee = halfYearFee,
            fullYearFee = fullYearFee,
            alumniFee = alumniFee,
        )
    }
}
