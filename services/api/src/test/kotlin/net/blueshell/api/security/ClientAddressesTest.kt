package net.blueshell.api.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class ClientAddressesTest {
    private val addresses = ClientAddresses("10.0.0.0/8, ,not-a-cidr")

    private fun request(
        remote: String,
        realIp: String? = null,
        forwardedFor: String? = null,
    ) = MockHttpServletRequest().apply {
        remoteAddr = remote
        realIp?.let { addHeader("X-Real-IP", it) }
        forwardedFor?.let { addHeader("X-Forwarded-For", it) }
    }

    @Test
    fun `an untrusted caller is its own address, whatever it claims`() {
        assertThat(addresses.resolve(request("203.0.113.9", realIp = "198.51.100.1"))).isEqualTo("203.0.113.9")
    }

    @Test
    fun `a trusted proxy is believed, X-Real-IP first`() {
        assertThat(addresses.resolve(request("10.0.0.2", realIp = "198.51.100.1", forwardedFor = "192.0.2.7"))).isEqualTo("198.51.100.1")
    }

    @Test
    fun `without X-Real-IP the first readable X-Forwarded-For entry wins`() {
        assertThat(addresses.resolve(request("10.0.0.2", forwardedFor = "garbage, [2001:DB8::1]:443, 192.0.2.7"))).isEqualTo("2001:db8::1")
    }

    @Test
    fun `a trusted proxy with no usable header is the proxy itself`() {
        assertThat(addresses.resolve(request("10.0.0.2", forwardedFor = "nope"))).isEqualTo("10.0.0.2")
    }

    @Test
    fun `an IPv4 address with a port loses the port`() {
        assertThat(addresses.resolve(request("10.0.0.2", realIp = "198.51.100.1:8080"))).isEqualTo("198.51.100.1")
    }

    @Test
    fun `unreadable or oversized values are ignored`() {
        assertThat(addresses.resolve(request(" "))).isEqualTo("unknown")
        assertThat(addresses.resolve(request("x".repeat(65)))).isEqualTo("unknown")
        assertThat(addresses.resolve(request("[]"))).isEqualTo("unknown")
        assertThat(addresses.resolve(request("1".repeat(46)))).isEqualTo("unknown")
    }
}
