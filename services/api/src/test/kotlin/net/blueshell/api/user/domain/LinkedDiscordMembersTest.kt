package net.blueshell.api.user.domain

import net.blueshell.api.user.persistence.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class LinkedDiscordMembersTest {
    @Test
    fun `names every Discord member an account has linked`() {
        val users: UserRepository =
            mock {
                on { findLinkedDiscordIds() } doReturn listOf("803", "804")
                on { findDiscordIdById(7) } doReturn "803"
            }

        assertThat(LinkedDiscordMembers(users).claimedIds()).containsExactlyInAnyOrder("803", "804")
        assertThat(LinkedDiscordMembers(users).discordIdOf(7)).isEqualTo("803")
    }
}
