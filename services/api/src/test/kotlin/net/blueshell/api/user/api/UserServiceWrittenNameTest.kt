package net.blueshell.api.user.api

import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.persistence.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class UserServiceWrittenNameTest {
    private val roos = Entities.user()
    private val repository: UserRepository =
        mock {
            on { findAllByWrittenName("Roos Kruk") } doReturn listOf(roos)
            on { findAllByWrittenName("Jan Jansen") } doReturn listOf(mock(), mock())
            on { findAllByWrittenName("Nobody") } doReturn emptyList()
        }
    private val service = UserService(repository, mock(), mock(), mock())

    @Test
    fun `names the one account that answers to a name`() {
        assertThat(service.findOnlyByWrittenName("Roos Kruk")).isSameAs(roos)
    }

    @Test
    fun `names nobody when two accounts answer, or none does`() {
        assertThat(service.findOnlyByWrittenName("Jan Jansen")).isNull()
        assertThat(service.findOnlyByWrittenName("Nobody")).isNull()
    }
}
