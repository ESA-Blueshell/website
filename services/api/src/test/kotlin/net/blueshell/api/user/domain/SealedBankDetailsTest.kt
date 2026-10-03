package net.blueshell.api.user.domain

import net.blueshell.api.user.domain.sealing.LocalSealer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SealedBankDetailsTest {
    private val sealer = LocalSealer()
    private val sealing = SealedBankDetails(sealer, "api-bank-details")
    private val iban = requireNotNull(Iban.parse("NL91ABNA0417164300"))

    @Test
    fun `an account holder copied onto another member shows as none rather than as theirs`() {
        val sealed = sealing.seal(7, iban, "Ann Vos")

        assertThat(sealing.accountHolder(HeldAccount(7, sealed))).isEqualTo("Ann Vos")
        assertThat(sealing.accountHolder(HeldAccount(8, sealed))).isNull()
    }

    @Test
    fun `a sealed IBAN that opens as something else than an IBAN is refused`() {
        val sealed = sealing.seal(7, iban, "Ann Vos")
        // The account holder sealed where the IBAN belongs: it opens under neither field's context.
        val swapped = SealedAccount(sealed.accountHolder, sealed.accountHolder)

        assertThatThrownBy { sealing.open(listOf(HeldAccount(7, swapped))) }.isInstanceOf(BankDetailsUnopenable::class.java)
    }
}
