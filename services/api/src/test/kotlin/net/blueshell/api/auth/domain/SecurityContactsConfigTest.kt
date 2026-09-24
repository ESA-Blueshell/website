package net.blueshell.api.auth.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SecurityContactsConfigTest {
    @Test
    fun `the contacts point at the board's address and the api's Discord redirects`() {
        val contacts = SecurityContactsConfig().securityContacts("board@example.org", "https://api.example.org")

        assertThat(contacts).isEqualTo(
            SecurityContacts(
                "board@example.org",
                "https://api.example.org/discord/channel/board",
                "https://api.example.org/discord/channel/suggestions",
            ),
        )
        assertThat(contacts.markdown).contains("[board@example.org](mailto:board@example.org)")
    }
}
