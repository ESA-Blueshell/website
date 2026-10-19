package net.blueshell.api.user.web

import jakarta.validation.Validation
import net.blueshell.api.security.StepUp
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.user.api.MaskedIban
import net.blueshell.api.user.domain.BankAccountPreview
import net.blueshell.api.user.domain.BankAccounts
import net.blueshell.api.user.persistence.BankAccount
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

class BankAccountControllerTest {
    private val accounts: BankAccounts = mock()
    private val currentUser: CurrentUserProvider = mock()
    private val stepUp: StepUp = mock()
    private val controller = BankAccountController(accounts, currentUser, stepUp)
    private val preview = BankAccountPreview(MaskedIban("DE", "00"), Instant.EPOCH)

    @Test
    fun `answers the reader's preview, or no content without one`() {
        whenever(currentUser.currentUser()).thenReturn(CurrentUser(12, emptySet(), null))
        whenever(accounts.own(12)).thenReturn(preview)

        assertThat(controller.findOwnBankAccount().body).isEqualTo(BankAccountPreviewResponse("DE", "00", Instant.EPOCH))
        whenever(currentUser.currentUser()).thenReturn(CurrentUser(13, emptySet(), null))
        assertThat(controller.findOwnBankAccount().statusCode).isEqualTo(HttpStatus.NO_CONTENT)

        whenever(currentUser.currentUser()).thenReturn(null)
        assertThatThrownBy { controller.findOwnBankAccount() }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `replacing and deleting the account each ask a step-up first`() {
        whenever(currentUser.currentUser()).thenReturn(CurrentUser(12, emptySet(), null))
        whenever(accounts.replace(12, "DE89370400440532013000", "Ann Vos", "COBADEFFXXX")).thenReturn(preview)

        val request = ReplaceBankAccountRequest("DE89370400440532013000", "Ann Vos", "COBADEFFXXX")
        assertThat(controller.replaceOwnBankAccount(request).ibanCountry).isEqualTo("DE")
        controller.deleteOwnBankAccount()

        verify(stepUp, org.mockito.kotlin.times(2)).require()
        verify(accounts).delete(12)
        assertThat(request.toString()).doesNotContain("0532").endsWith("3000)")
        assertThat(Validation.buildDefaultValidatorFactory().validator.validate(request.copy(iban = " "))).isNotEmpty
    }

    @Test
    fun `nothing of the account reaches a log line`() {
        assertThat(BankAccount("vault:v1:x", "vault:v1:y", null, "NL00", 2, Instant.EPOCH).toString()).isEqualTo("BankAccount(NL00, v2)")
        assertThat(ReplaceBankAccountRequest("NL91ABNA0417164300", "Ann Vos").toString())
            .contains("4300")
            .doesNotContain("0417")
            .doesNotContain("Ann Vos")
        // The no-argument constructor JPA loads a row through.
        assertThat(BankAccount::class.java.getDeclaredConstructor().newInstance()).isNotNull
    }
}
