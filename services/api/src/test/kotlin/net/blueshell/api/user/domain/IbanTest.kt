package net.blueshell.api.user.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IbanTest {
    @Test
    fun `reads an IBAN typed with spaces and in lower case, and shows only its country code and last two`() {
        val iban = Iban.parse("nl91 abna 0417 1643 00")

        assertThat(iban?.value).isEqualTo("NL91ABNA0417164300")
        assertThat(iban?.masked).isEqualTo("NL00")
        assertThat(
            net.blueshell.api.user.api.MaskedIban
                .of("NL0"),
        ).isNull()
        assertThat(
            net.blueshell.api.user.api.MaskedIban
                .of(null),
        ).isNull()
        assertThat(iban.toString()).isEqualTo("Iban(NL•• … ••00)").doesNotContain("0417")
    }

    @Test
    fun `refuses a wrong check digit and anything not shaped like an IBAN`() {
        assertThat(Iban.parse("NL92ABNA0417164300")).isNull()
        assertThat(Iban.parse("NL91")).isNull()
        assertThat(Iban.parse("1234567890123456")).isNull()
    }
}
