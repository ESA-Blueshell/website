package net.blueshell.api.user.api

import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.persistence.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ServiceAccountTest {
    private val users = mock<UserRepository>()
    private val account = ServiceAccount(users)

    @Test
    fun `the service account is the user holding the service role`() {
        val system = Entities.user(id = 1, username = "system")
        whenever(users.findServiceAccounts()).thenReturn(listOf(system))

        assertThat(account.orNull("a cover")).isSameAs(system)
    }

    @Test
    fun `a database without one answers none rather than failing`() {
        whenever(users.findServiceAccounts()).thenReturn(emptyList())

        assertThat(account.orNull("a cover")).isNull()
    }
}
