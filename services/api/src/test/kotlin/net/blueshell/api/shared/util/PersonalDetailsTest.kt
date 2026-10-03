package net.blueshell.api.shared.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PersonalDetailsTest {
    @Test
    fun `a unique-key refusal keeps its key and loses its value`() {
        val refused = "(conn=12) Duplicate entry '+31687654321-9999-12-31 23:59:59' for key 'uk_users_phone_number_deleted_at'"

        assertThat(PersonalDetails.scrub(refused))
            .isEqualTo("(conn=12) Duplicate entry '[value]' for key 'uk_users_phone_number_deleted_at'")
        assertThat(PersonalDetails.scrub("Incorrect string value: '\\xF0\\x9F' for column 'first_name'"))
            .isEqualTo("Incorrect string value: '[value]' for column 'first_name'")
    }

    @Test
    fun `a JSON parse error keeps the type and loses the value`() {
        assertThat(PersonalDetails.scrub("Cannot deserialize value of type `java.lang.Long` from String \"+31687654321\": not a valid"))
            .isEqualTo("Cannot deserialize value of type `java.lang.Long` from String \"[value]\": not a valid")
        assertThat(PersonalDetails.scrub("Cannot coerce String value (\"Jan Jansen\") to `int`"))
            .isEqualTo("Cannot coerce String value (\"[value]\") to `int`")
        assertThat(PersonalDetails.scrub("Unrecognized token 'Jansen': was expecting JSON"))
            .isEqualTo("Unrecognized token '[value]': was expecting JSON")
    }

    @Test
    fun `email addresses and IBANs are masked wherever they are`() {
        assertThat(PersonalDetails.scrub("mail to ann.de-vries+club@student.utwente.nl bounced"))
            .isEqualTo("mail to [email] bounced")
        assertThat(PersonalDetails.scrub("mandates NL91ABNA0417164300 and NL91 ABNA 0417 1643 00"))
            .isEqualTo("mandates [iban] and [iban]")
    }

    @Test
    fun `text without personal details is left as it is`() {
        val plain = "Job execution 42 failed (attempt 1/4). errorType=java.lang.IllegalStateException, ts=2026-10-02T10:00:00Z"

        assertThat(PersonalDetails.scrub(plain)).isEqualTo(plain)
    }
}
