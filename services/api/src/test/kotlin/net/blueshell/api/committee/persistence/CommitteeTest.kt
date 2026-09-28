package net.blueshell.api.committee.persistence

import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class CommitteeTest {
    private fun loadedWithoutAddress(name: String) =
        Committee(name = name, description = "").also {
            Committee::class.java
                .getDeclaredField("slug")
                .apply { isAccessible = true }
                .set(it, null)
        }

    @Test
    fun `a committee loaded without an address answers to the one its name makes`() {
        val committee = loadedWithoutAddress("Lan Cie!")

        committee.addressFromName()

        assertThat(committee.slug).isEqualTo("lan-cie")
    }

    @Test
    fun `a committee loaded with an address keeps it`() {
        val committee = Committee(name = "LanCie", description = "", slug = "lan")

        committee.addressFromName()

        assertThat(committee.slug).isEqualTo("lan")
    }

    @Test
    fun `a committee knows its members and nobody else`() {
        val member = mock<User> { on { id } doReturn 7L }
        val outsider = mock<User> { on { id } doReturn 8L }
        val committee = Committee(name = "LanCie", description = "")
        committee.replaceMembers(listOf(CommitteeMember(committee = committee, user = member)))

        assertThat(committee.hasMember(member)).isTrue
        assertThat(committee.hasMember(outsider)).isFalse
        assertThat(committee.hasMember(null as User?)).isFalse
    }
}
