package net.blueshell.api.platform.config

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ProductionSecurityHardeningGuardTest {

    private val strongSecret = "2goYh5PqH6dPkWWXLUJQ4QY6nD2YgR5qk9+6Yu8aITR7cfwxkuNolL9zkgf2qHFxifWdbxG+E+XqMIKkt3ibDw=="

    @Test
    fun `accepts secure production configuration`() {
        val guard = ProductionSecurityHardeningGuard(
            jwtSecret = strongSecret,
            requireHttps = true,
            openApiPublicEnabled = false
        )

        assertThatCode { guard.validate() }.doesNotThrowAnyException()
    }

    @Test
    fun `rejects non base64 jwt secret`() {
        val guard = ProductionSecurityHardeningGuard(
            jwtSecret = "not-base64",
            requireHttps = true,
            openApiPublicEnabled = false
        )

        assertThatThrownBy { guard.validate() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Base64")
    }

    @Test
    fun `rejects weak production toggles`() {
        val guard = ProductionSecurityHardeningGuard(
            jwtSecret = strongSecret,
            requireHttps = false,
            openApiPublicEnabled = true
        )

        assertThatThrownBy { guard.validate() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("require-https")
    }
}
