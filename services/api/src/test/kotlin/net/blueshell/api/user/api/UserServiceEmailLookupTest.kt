package net.blueshell.api.user.api

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.persistence.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class UserServiceEmailLookupTest {
    private val ada = Entities.user(email = "ada@example.com")

    @Test
    fun `looks addresses up trimmed and lower case`() {
        val repository: UserRepository = mock { on { findAllByEmailIn(setOf("ada@example.com")) } doReturn listOf(ada) }

        val found = UserService(repository, mock(), mock(), mock()).findAllByEmails(listOf(" Ada@Example.com", "ada@example.com"))

        assertThat(found).containsExactly(ada)
    }

    @Test
    fun `no addresses asks nothing`() {
        val repository: UserRepository = mock()

        assertThat(UserService(repository, mock(), mock(), mock()).findAllByEmails(emptyList())).isEmpty()
        verifyNoInteractions(repository)
    }

    @Test
    fun `names everybody holding a role once`() {
        val repository = mock<UserRepository>()
        whenever(repository.findIdsHolding(Role.BOARD)).thenReturn(listOf(3, 3, 4))
        assertThat(UserService(repository, mock(), mock(), mock()).findIdsHolding(Role.BOARD)).containsExactlyInAnyOrder(3, 4)
    }
}
