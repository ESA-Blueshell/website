package net.blueshell.api.user.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.shared.user.MemberIdentity
import net.blueshell.api.user.persistence.MemberIdentityRow
import net.blueshell.api.user.persistence.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MemberIdentityLookupTest {
    private val users = mockk<UserRepository>()
    private val lookup = MemberIdentityLookup(users)

    private fun row(
        id: Long,
        username: String,
        discordId: String?,
    ) = object : MemberIdentityRow {
        override val id = id
        override val username = username
        override val discordId = discordId
    }

    @Test
    fun `no ids asks nothing and returns empty`() {
        assertThat(lookup.of(emptyList())).isEmpty()

        verify(exactly = 0) { users.findMemberIdentities(any()) }
    }

    @Test
    fun `each id maps to its username and linked Discord id`() {
        every { users.findMemberIdentities(listOf(1, 2)) } returns
            listOf(row(1, "ann", discordId = "42"), row(2, "bob", discordId = null))

        assertThat(lookup.of(listOf(1, 2)))
            .isEqualTo(
                mapOf(
                    1L to MemberIdentity(username = "ann", discordId = "42"),
                    2L to MemberIdentity(username = "bob", discordId = null),
                ),
            )
    }
}
