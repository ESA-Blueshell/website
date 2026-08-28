package net.blueshell.api.shared.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LogSanitizerTest {

    @Test
    fun `a carriage return cannot start a second log record`() {
        assertThat(sanitizeForLog("real\rforged")).isEqualTo("real_forged")
    }

    @Test
    fun `a line feed cannot start a second log record`() {
        assertThat(sanitizeForLog("real\nforged")).isEqualTo("real_forged")
    }

    @Test
    fun `a CRLF pair collapses to one replacement rather than two`() {
        // The line-break pass matches the pair as a single break, so a Windows-style newline
        // does not read as two forged records. Replacing each character separately would
        // produce "real__forged".
        assertThat(sanitizeForLog("real\r\nforged")).isEqualTo("real_forged")
    }

    @Test
    fun `other control characters go too`() {
        assertThat(sanitizeForLog("tab\there")).isEqualTo("tab_here")
        assertThat(sanitizeForLog("nul\u0000byte")).isEqualTo("nul_byte")
        assertThat(sanitizeForLog("esc\u001B[31m")).isEqualTo("esc_[31m")
    }

    @Test
    fun `a null becomes the placeholder rather than throwing`() {
        assertThat(sanitizeForLog(null)).isEqualTo("<null>")
    }

    @Test
    fun `ordinary text is left alone`() {
        assertThat(sanitizeForLog("banner.png")).isEqualTo("banner.png")
        assertThat(sanitizeForLog("Ohm, Sweet Ohm - 2020/21")).isEqualTo("Ohm, Sweet Ohm - 2020/21")
    }

    @Test
    fun `a value that is not a string is rendered before it is scrubbed`() {
        assertThat(sanitizeForLog(42)).isEqualTo("42")
    }
}
