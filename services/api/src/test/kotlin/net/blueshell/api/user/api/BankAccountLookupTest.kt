package net.blueshell.api.user.api

import net.blueshell.api.user.domain.BankAccountPreview
import net.blueshell.api.user.domain.BankAccounts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class BankAccountLookupTest {
    @Test
    fun `tells whether a person has a bank account on file`() {
        val accounts: BankAccounts = mock()
        whenever(accounts.own(12)).thenReturn(BankAccountPreview(MaskedIban("NL", "00"), Instant.EPOCH))

        assertThat(BankAccountLookup(accounts).isOnFile(12)).isTrue()
        assertThat(BankAccountLookup(accounts).isOnFile(13)).isFalse()
    }
}
