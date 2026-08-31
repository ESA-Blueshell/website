package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate
import java.util.Locale

/** The direct-debit pre-notification: what will be taken, when, and why that amount. */
class IncassoNotificationEmailBuilderTest {

    private val originalLocale: Locale = Locale.getDefault()

    @AfterEach
    fun restoreDefaultLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `states the amount, the debit date and the reason the amount applies`() {
        val member = member("Alice Regular")
        val period = period(startDate = LocalDate.of(2025, 9, 1), endDate = LocalDate.of(2026, 8, 31))

        val email = createIncassoNotificationEmail(
            member,
            period,
            BulkFeeType.FULL_YEAR_FEE,
            LocalDate.of(2025, 10, 15),
        )

        assertThat(email.recipientEmail).isEqualTo(member.email)
        assertThat(email.recipientName).isEqualTo("Alice Regular")
        assertThat(email.subject)
            .isEqualTo("Your Blueshell contribution will be collected automatically (2025/2026)")
        assertThat(email.markdownContent)
            .contains("Dear Alice Regular")
            .contains("Amount to be collected: €45,00")
            .contains("the full-year fee")
            .contains("15 October 2025")
    }

    // A member on direct debit who is asked to transfer pays twice, which is why this
    // email asks for nothing.
    @Test
    fun `asks for no transfer and quotes no bank account`() {
        val email = createIncassoNotificationEmail(
            member("Bob Regular"),
            period(),
            BulkFeeType.FULL_YEAR_FEE,
            LocalDate.now().plusMonths(1),
        )

        assertThat(email.markdownContent)
            .contains("You do not need to transfer anything yourself")
            .doesNotContain("Bank transfer")
            .doesNotContain("IBAN")
            .doesNotContain("BIC")
    }

    @ParameterizedTest
    @EnumSource(BulkFeeType::class)
    fun `never quotes an amount without the reason for it`(feeType: BulkFeeType) {
        val email = createIncassoNotificationEmail(
            member("Carol Regular"),
            period(),
            feeType,
            LocalDate.now().plusMonths(1),
        )

        assertThat(email.markdownContent).contains(feeReason(feeType))
    }

    @Test
    fun `quotes the amount the fee type prices, not another one`() {
        val email = createIncassoNotificationEmail(
            member("Dave Alumni"),
            period(),
            BulkFeeType.ALUMNI_FEE,
            LocalDate.now().plusMonths(1),
        )

        assertThat(email.markdownContent)
            .contains("Amount to be collected: €10,00")
            .doesNotContain("€45,00")
            .doesNotContain("€25,00")
    }

    @ParameterizedTest
    @ValueSource(strings = ["nl-NL", "en-US", "de-DE"])
    fun `formats currency in Dutch notation whatever the JVM default locale is`(languageTag: String) {
        Locale.setDefault(Locale.forLanguageTag(languageTag))

        val email = createIncassoNotificationEmail(
            member("Eve Regular"),
            period(halfYearFee = 12.50),
            BulkFeeType.HALF_YEAR_FEE,
            LocalDate.now().plusMonths(1),
        )

        assertThat(email.markdownContent).contains("€12,50")
    }

    private fun member(fullName: String): User {
        val (firstName, lastName) = fullName.split(" ")
        return User(
            username = firstName.lowercase(),
            email = "${firstName.lowercase()}@example.com",
            password = "dummy",
            initials = "${firstName.first()}${lastName.first()}",
            firstName = firstName,
            lastName = lastName,
            phoneNumber = "0612345678",
            discord = "${firstName.lowercase()}#0001",
        )
    }

    private fun period(
        startDate: LocalDate = LocalDate.now(),
        endDate: LocalDate = LocalDate.now().plusMonths(6),
        halfYearFee: Double = 25.0,
        fullYearFee: Double = 45.0,
        alumniFee: Double = 10.0,
    ) = ContributionPeriod(
        startDate = startDate,
        endDate = endDate,
        halfYearCutoffDate = startDate.plusMonths(3),
        halfYearFee = halfYearFee,
        fullYearFee = fullYearFee,
        alumniFee = alumniFee,
    )
}
