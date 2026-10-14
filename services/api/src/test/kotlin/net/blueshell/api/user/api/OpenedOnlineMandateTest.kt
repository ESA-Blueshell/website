package net.blueshell.api.user.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class OpenedOnlineMandateTest {
    @Test
    fun `names only its reference when logged, never the account or the address`() {
        val opened =
            OpenedOnlineMandate(
                userId = 7,
                username = "ann",
                reference = "BLUESHELL-7-20260901",
                signedOn = LocalDate.of(2026, 9, 1),
                authorisedAt = Instant.parse("2026-09-01T10:00:00Z"),
                wordingVersion = "2026-10",
                wording = "I authorise",
                accountHolder = "Ann Vos",
                iban = "NL91ABNA0417164300",
                address = AddressFields("NL", "Enschede", "Hallenweg", "5", "7522NH"),
            )

        assertThat(opened.toString()).isEqualTo("OpenedOnlineMandate(BLUESHELL-7-20260901)")
    }
}
