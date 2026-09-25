package net.blueshell.api.auth.domain.twofactor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64

class SecretCipherTest {
    private val keyA = Base64.getEncoder().encodeToString(ByteArray(32) { 1 })
    private val keyB = Base64.getEncoder().encodeToString(ByteArray(32) { 2 })

    @Test
    fun `a secret comes back as it went in, and the ciphertext is not it`() {
        val cipher = SecretCipher("a", keyA, "")
        val sealed = cipher.seal("hello".toByteArray())

        assertThat(sealed.keyId).isEqualTo("a")
        assertThat(sealed.ciphertext).doesNotContain("hello")
        assertThat(cipher.open(sealed)).isEqualTo("hello".toByteArray())
    }

    @Test
    fun `the same secret sealed twice reads differently`() {
        val cipher = SecretCipher("a", keyA, "")

        assertThat(cipher.seal("x".toByteArray()).ciphertext).isNotEqualTo(cipher.seal("x".toByteArray()).ciphertext)
    }

    @Test
    fun `a new key seals while a retired one still opens`() {
        val old = SecretCipher("a", keyA, "").seal("hello".toByteArray())
        val rotated = SecretCipher("b", keyB, "a:$keyA")

        assertThat(rotated.open(old)).isEqualTo("hello".toByteArray())
        assertThat(rotated.seal("x".toByteArray()).keyId).isEqualTo("b")
    }

    @Test
    fun `tampered ciphertext and an unknown key fail closed`() {
        val cipher = SecretCipher("a", keyA, "")
        val sealed = cipher.seal("hello".toByteArray())
        val bytes = Base64.getDecoder().decode(sealed.ciphertext)
        bytes[bytes.size - 1] = (bytes.last() + 1).toByte()

        assertThrows<IllegalStateException> { cipher.open(sealed.copy(ciphertext = Base64.getEncoder().encodeToString(bytes))) }
        assertThrows<IllegalStateException> { cipher.open(sealed.copy(keyId = "z")) }
    }

    @Test
    fun `a key that is not 32 bytes is refused`() {
        assertThrows<IllegalArgumentException> { SecretCipher("a", Base64.getEncoder().encodeToString(ByteArray(16)), "") }
    }
}
