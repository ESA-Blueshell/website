package net.blueshell.api.platform.config

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Base64

class ProductionSecurityHardeningGuardTest {
    private val jwtSecret = Base64.getEncoder().encodeToString(ByteArray(64) { 7 })

    private fun guard(twoFactorKey: String) = ProductionSecurityHardeningGuard(jwtSecret, true, false, twoFactorKey)

    @Test
    fun `production starts with a two-factor key of thirty-two bytes`() {
        assertThatCode { guard(Base64.getEncoder().encodeToString(ByteArray(32) { 1 })).validate() }.doesNotThrowAnyException()
    }

    @Test
    fun `production refuses to start without a two-factor key, or with one of the wrong size`() {
        assertThatThrownBy { guard("").validate() }.hasMessageContaining("TWO_FACTOR_ENCRYPTION_KEY")
        assertThatThrownBy { guard(Base64.getEncoder().encodeToString(ByteArray(16))).validate() }
            .hasMessageContaining("TWO_FACTOR_ENCRYPTION_KEY")
        assertThatThrownBy { guard("not base64 at all!").validate() }.hasMessageContaining("TWO_FACTOR_ENCRYPTION_KEY")
    }
}
