package net.blueshell.api.auth.domain.twofactor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Instant

class TotpTest {
    private val rfc6238AppendixBSecret = "12345678901234567890".toByteArray()

    @ParameterizedTest
    @CsvSource("59,287082", "1111111109,081804", "1111111111,050471", "1234567890,005924", "2000000000,279037")
    fun `codes match the RFC 6238 vectors`(
        seconds: Long,
        code: String,
    ) {
        assertThat(Totp.code(rfc6238AppendixBSecret, Totp.stepAt(Instant.ofEpochSecond(seconds)))).isEqualTo(code)
    }

    @Test
    fun `a code from the step before or after is accepted, two steps away is not`() {
        val now = Instant.ofEpochSecond(1111111111)
        val step = Totp.stepAt(now)

        assertThat(Totp.acceptedStep(rfc6238AppendixBSecret, Totp.code(rfc6238AppendixBSecret, step - 1), now, null)).isEqualTo(step - 1)
        assertThat(Totp.acceptedStep(rfc6238AppendixBSecret, Totp.code(rfc6238AppendixBSecret, step + 1), now, null)).isEqualTo(step + 1)
        assertThat(Totp.acceptedStep(rfc6238AppendixBSecret, Totp.code(rfc6238AppendixBSecret, step - 2), now, null)).isNull()
    }

    @Test
    fun `a code at or before the last accepted step is a replay`() {
        val now = Instant.ofEpochSecond(1111111111)
        val step = Totp.stepAt(now)
        val code = Totp.code(rfc6238AppendixBSecret, step)

        assertThat(Totp.acceptedStep(rfc6238AppendixBSecret, code, now, lastUsedStep = step)).isNull()
        assertThat(Totp.acceptedStep(rfc6238AppendixBSecret, code, now, lastUsedStep = step - 1)).isEqualTo(step)
    }

    @Test
    fun `anything but six digits is refused`() {
        val now = Instant.ofEpochSecond(59)
        assertThat(Totp.acceptedStep(rfc6238AppendixBSecret, "28708", now, null)).isNull()
        assertThat(Totp.acceptedStep(rfc6238AppendixBSecret, "abcdef", now, null)).isNull()
        assertThat(Totp.acceptedStep(rfc6238AppendixBSecret, " 287 082 ", now, null)).isEqualTo(Totp.stepAt(now))
    }

    @Test
    fun `a secret travels as unpadded base32 and back`() {
        val secret = Totp.newSecret()

        assertThat(secret).hasSize(20)
        assertThat(Base32.encode(secret)).matches("[A-Z2-7]{32}")
        assertThat(Base32.decode(Base32.encode(secret))).isEqualTo(secret)
        assertThat(Base32.encode("12345678901234567890".toByteArray())).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
    }

    @Test
    fun `the otpauth uri names the issuer, the account and the parameters`() {
        val uri = Totp.otpauthUri("ESA Blueshell", "jane doe", "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")

        assertThat(uri).isEqualTo(
            "otpauth://totp/ESA%20Blueshell:jane%20doe?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" +
                "&issuer=ESA%20Blueshell&algorithm=SHA1&digits=6&period=30",
        )
    }
}
