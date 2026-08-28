package net.blueshell.api.oidc.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Unit tests for [ForwardAuthController.isSafeRedirectTarget].
 *
 * The allowlist guard must accept only the service hostnames listed in
 * [ForwardAuthController.HOST_ROLE] and reject every other value,
 * including look-alike and protocol-relative tricks (CodeQL #471).
 */
class ForwardAuthControllerAllowlistTest {

    private val controller = ForwardAuthController(frontendBaseUrl = "https://esa-blueshell.nl")

    @ParameterizedTest(name = "allows known host: {0}")
    @ValueSource(
        strings = [
            "vault.esa-blueshell.nl",
            "headlamp.esa-blueshell.nl",
            "stalwart.esa-blueshell.nl",
            "traefik.esa-blueshell.nl",
            // Case-insensitive match
            "VAULT.ESA-BLUESHELL.NL",
            "Stalwart.Esa-Blueshell.NL",
        ],
    )
    fun `isSafeRedirectTarget returns true for known service hosts`(host: String) {
        assertThat(controller.isSafeRedirectTarget(host)).isTrue()
    }

    @ParameterizedTest(name = "rejects untrusted host: {0}")
    @ValueSource(
        strings = [
            "evil.attacker.com",
            "rogue.example.com",
            "esa-blueshell.nl",          // The frontend root is not a forwarded service
            "vault.esa-blueshell.nl.evil.com",  // Suffix-match bypass attempt
            "xvault.esa-blueshell.nl",   // Prefix-match bypass attempt
            "",                          // Empty string
            "localhost",
        ],
    )
    fun `isSafeRedirectTarget returns false for untrusted or unknown hosts`(host: String) {
        assertThat(controller.isSafeRedirectTarget(host)).isFalse()
    }

    @Test
    fun `HOST_ROLE contains exactly the expected service hosts`() {
        assertThat(ForwardAuthController.HOST_ROLE.keys).containsExactlyInAnyOrder(
            "vault.esa-blueshell.nl",
            "headlamp.esa-blueshell.nl",
            "stalwart.esa-blueshell.nl",
            "traefik.esa-blueshell.nl",
        )
    }
}
