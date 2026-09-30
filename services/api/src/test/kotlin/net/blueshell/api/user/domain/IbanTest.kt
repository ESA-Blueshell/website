package net.blueshell.api.user.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IbanTest {
    @Test
    fun `reads an IBAN typed with spaces and in lower case, and shows only its last four`() {
        val iban = Iban.parse("nl91 abna 0417 1643 00")

        assertThat(iban?.value).isEqualTo("NL91ABNA0417164300")
        assertThat(iban?.lastFour).isEqualTo("4300")
        assertThat(iban.toString()).isEqualTo("Iban(****4300)").doesNotContain("0417")
    }

    @Test
    fun `refuses a wrong check digit and anything not shaped like an IBAN`() {
        assertThat(Iban.parse("NL92ABNA0417164300")).isNull()
        assertThat(Iban.parse("NL91")).isNull()
        assertThat(Iban.parse("1234567890123456")).isNull()
    }
}
