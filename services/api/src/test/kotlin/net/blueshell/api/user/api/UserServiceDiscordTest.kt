package net.blueshell.api.user.api

import net.blueshell.api.user.persistence.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class UserServiceDiscordTest {
    @Test
    fun `asks whether a Discord member is linked, to any account or to another`() {
        val repository: UserRepository =
            mock {
                on { existsByDiscordId("803") } doReturn true
                on { existsByDiscordIdAndIdNot("803", 7) } doReturn false
            }
        val service = UserService(repository, mock(), mock(), mock())

        assertThat(service.existsByDiscordId("803")).isTrue()
        assertThat(service.existsByDiscordIdAndIdNot("803", 7)).isFalse()
    }
}
