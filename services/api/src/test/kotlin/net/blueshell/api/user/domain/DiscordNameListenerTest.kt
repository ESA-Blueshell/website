package net.blueshell.api.user.domain

import net.blueshell.api.shared.discord.DiscordMemberNamed
import net.blueshell.api.user.persistence.UserRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class DiscordNameListenerTest {
    @Test
    fun `renames the account linked to the member`() {
        val users: UserRepository = mock()

        DiscordNameListener(users).on(DiscordMemberNamed("803", "Nelly B"))

        verify(users).renameDiscordMember("803", "Nelly B")
    }
}
