package net.blueshell.api.file.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

/** The address bytes are stored at, which is the whole of what makes storing them deduplicate. */
class ContentAddressTest {

    @Test
    fun `the address of some bytes is their SHA-256 in lower-case hex`() {
        assertThat(address("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }

    @Test
    fun `no bytes still have an address`() {
        assertThat(address(""))
            .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    }

    @Test
    fun `the same bytes always land at the same address`() {
        assertThat(address("holiday")).isEqualTo(address("holiday"))
    }

    @Test
    fun `one byte apart is a different address`() {
        assertThat(address("holiday")).isNotEqualTo(address("holidaz"))
    }

    private fun address(content: String): String =
        ContentAddress.of(ByteArrayInputStream(content.toByteArray()))
}
