package net.blueshell.api.user.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.cloud.context.environment.EnvironmentChangeEvent
import org.springframework.mock.env.MockEnvironment
import java.util.Base64

class BankDetailsCipherTest {
    private val first = Base64.getEncoder().encodeToString(ByteArray(32) { 1 })
    private val second = Base64.getEncoder().encodeToString(ByteArray(32) { 2 })
    private val environment = MockEnvironment()
    private val cipher = BankDetailsCipher("1", first, "", environment)

    @Test
    fun `seals so the value is unreadable, and opens it again`() {
        val sealed = cipher.seal("NL91ABNA0417164300")

        assertThat(sealed.ciphertext).doesNotContain("NL91").doesNotContain("0417164300")
        assertThat(cipher.open(sealed)).isEqualTo("NL91ABNA0417164300")
    }

    @Test
    fun `takes a key rotated in Vault, and still opens what the old key sealed`() {
        val before = cipher.seal("Ann Vos")
        environment.setProperty("app.bank-details.key-id", "2")
        environment.setProperty("app.bank-details.key", second)
        environment.setProperty("app.bank-details.retired-keys", "1:$first")

        cipher.onChange(EnvironmentChangeEvent(setOf("app.bank-details.key")))

        assertThat(cipher.seal("x").keyId).isEqualTo("2")
        assertThat(cipher.open(before)).isEqualTo("Ann Vos")
    }

    @Test
    fun `falls back to the two-factor keys, ignores other changes, and keeps its keys on a malformed one`() {
        environment.setProperty("app.two-factor.key-id", "3")
        environment.setProperty("app.two-factor.key", second)
        cipher.onChange(EnvironmentChangeEvent(setOf("app.jwt.secret")))
        assertThat(cipher.seal("x").keyId).isEqualTo("1")

        cipher.onChange(EnvironmentChangeEvent(setOf("app.two-factor.key")))
        assertThat(cipher.seal("x").keyId).isEqualTo("3")

        environment.setProperty("app.two-factor.key", "too-short")
        cipher.onChange(EnvironmentChangeEvent(setOf("app.two-factor.key")))
        assertThat(cipher.seal("x").keyId).isEqualTo("3")
        assertThatThrownBy { BankDetailsCipher("1", "c2hvcnQ=", "", environment) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
