package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate
import java.util.Locale

/**
 * Tests for contribution reminder email builder.
 *
 * Verifies EmailContent is created correctly with all payment options (ADR-019, ADR-022).
 */
class ContributionReminderEmailBuilderTest {

    private val frontendUrl = "https://test-frontend.com"
    private val originalLocale: Locale = Locale.getDefault()

    @AfterEach
    fun restoreDefaultLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `createContributionReminderEmail builds correct EmailContent`() {
        // Given: User and contribution period
        val user = createTestUser("john.doe", "john.doe@example.com", "John", "Doe")
        val period = createTestPeriod(
            startDate = LocalDate.of(2024, 1, 1),
            endDate = LocalDate.of(2024, 12, 31),
            halfYearFee = 25.0,
            fullYearFee = 45.0,
            alumniFee = 10.0
        )

        // When: Building contribution reminder email
        val emailContent = createContributionReminderEmail(user, period, frontendUrl)

        // Then: EmailContent has correct fields
        assertThat(emailContent.recipientEmail).isEqualTo(user.email)
        assertThat(emailContent.recipientName).isEqualTo(user.fullName)
        assertThat(emailContent.subject).isEqualTo("Contribution Payment Reminder - Blueshell Esports")
        assertThat(emailContent.senderNameOverride).isEqualTo("Treasurer of Blueshell")
        assertThat(emailContent.replyToOverride).isEqualTo("board@blueshell.utwente.nl")

        // And: Body contains all payment options
        assertThat(emailContent.markdownContent)
            .contains("Dear John Doe")
            .contains("2024-01-01")
            .contains("2024-12-31")
            .contains("Half year fee: €25,00")
            .contains("Full year fee: €45,00")
            .contains("Alumni fee: €10,00")
            .contains(frontendUrl)
    }

    @Test
    fun `email includes friendly reminder language`() {
        // Given: User and period
        val user = createTestUser("jane", "jane@example.com", "Jane", "Smith")
        val period = createTestPeriod()

        // When: Building email
        val emailContent = createContributionReminderEmail(user, period, frontendUrl)

        // Then: Email has friendly tone
        assertThat(emailContent.markdownContent)
            .contains("friendly reminder")
            .contains("at your earliest convenience")
            .contains("If you have already made your payment, please disregard this message")
            .contains("Kind regards")
            .contains("Treasurer of Blueshell Esports")
    }

    @ParameterizedTest
    @ValueSource(strings = ["nl-NL", "en-US", "de-DE"])
    fun `email formats currency in Dutch notation whatever the JVM default locale is`(languageTag: String) {
        // Given: A JVM default locale that must not influence the rendered amounts
        Locale.setDefault(Locale.forLanguageTag(languageTag))
        val user = createTestUser("test", "test@example.com", "Test", "User")
        val period = createTestPeriod(
            halfYearFee = 12.50,
            fullYearFee = 20.00,
            alumniFee = 5.99
        )

        // When: Building email
        val emailContent = createContributionReminderEmail(user, period, frontendUrl)

        // Then: Amounts carry two decimals behind a comma
        assertThat(emailContent.markdownContent)
            .contains("€12,50")
            .contains("€20,00")
            .contains("€5,99")
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
            halfYearCutoffDate = startDate.plusMonths(6),
            halfYearFee = halfYearFee,
            fullYearFee = fullYearFee,
            alumniFee = alumniFee,
        )
    }
}
