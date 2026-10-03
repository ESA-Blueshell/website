package net.blueshell.api.user.api

import net.blueshell.api.user.domain.BankDetails
import net.blueshell.api.user.domain.Iban
import net.blueshell.api.user.domain.Mandates
import net.blueshell.api.user.persistence.IncassoMandate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

class CollectionAccountsTest {
    @Test
    fun `opens a mandate's account in full, and never logs it`() {
        val mandates: Mandates = mock()
        val mandate = IncassoMandate("k1", "sealed", "sealed", "4300", "BLUESHELL-1", LocalDate.of(2025, 9, 3), null, Instant.EPOCH)
        whenever(mandates.bankDetailsOf(mandate)).thenReturn(BankDetails(requireNotNull(Iban.parse("NL91ABNA0417164300")), "Ann Vos"))

        val account = CollectionAccounts(mandates).of(mandate)

        assertThat(account.iban).isEqualTo("NL91ABNA0417164300")
        assertThat(account.accountHolder).isEqualTo("Ann Vos")
        assertThat(account.toString()).contains("4300").doesNotContain("0417")
    }
}
