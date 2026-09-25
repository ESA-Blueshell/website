package net.blueshell.api.auth.domain.twofactor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BackupCodesTest {
    @Test
    fun `ten codes of two groups of five, all different`() {
        val codes = BackupCodes.generate()

        assertThat(codes).hasSize(10).doesNotHaveDuplicates()
        codes.forEach { assertThat(it).matches("[a-z2-9]{5}-[a-z2-9]{5}") }
    }

    @Test
    fun `a code hashes the same however it is typed`() {
        assertThat(BackupCodes.hash(" ABCDE-fghjk ")).isEqualTo(BackupCodes.hash("abcdefghjk"))
        assertThat(BackupCodes.hash("abcde-fghjk")).hasSize(64).isNotEqualTo(BackupCodes.hash("abcde-fghjm"))
    }

    @Test
    fun `a backup code is told apart from an authenticator code`() {
        assertThat(BackupCodes.looksLikeOne("abcde-fghjk")).isTrue()
        assertThat(BackupCodes.looksLikeOne("123456")).isFalse()
        assertThat(BackupCodes.looksLikeOne("123 456")).isFalse()
    }
}
