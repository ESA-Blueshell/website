package net.blueshell.api.user.api

import net.blueshell.api.user.domain.BankAccountPreview
import net.blueshell.api.user.domain.BankAccounts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

class BankAccountLookupTest {
    @Test
    fun `tells whether a person has a bank account on file`() {
        val accounts: BankAccounts = mock()
        whenever(accounts.own(12)).thenReturn(BankAccountPreview(MaskedIban("NL", "00"), Instant.EPOCH))

        assertThat(BankAccountLookup(accounts).isOnFile(12)).isTrue()
        assertThat(BankAccountLookup(accounts).isOnFile(13)).isFalse()
    }

    @Test
    fun `hands the board the account to pay to and records a payout, through the bank accounts`() {
        val accounts: BankAccounts = mock()
        val account = BankAccountToPay("Ann Vos", MaskedIban("NL", "00"), null)
        whenever(accounts.toPay(12)).thenReturn(account)
        whenever(accounts.recordPaidTo(12, LocalDate.EPOCH)).thenReturn(MaskedIban("NL", "00"))

        assertThat(BankAccountLookup(accounts).toPay(12)).isEqualTo(account)
        assertThat(BankAccountLookup(accounts).recordPaidTo(12, LocalDate.EPOCH)).isEqualTo(MaskedIban("NL", "00"))
        // Only the masked IBAN reaches a log line.
        assertThat(account.toString()).isEqualTo("BankAccountToPay(NL•• … ••00)")
    }
}
