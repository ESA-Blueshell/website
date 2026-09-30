package net.blueshell.api.committee.persistence

import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CommitteeTest {
    @Test
    fun `a committee knows its members and nobody else`() {
        val member = Entities.user(id = 7L)
        val outsider = Entities.user(id = 8L)
        val committee = Committee(name = "LanCie", description = "")
        committee.replaceMembers(listOf(CommitteeMember(committee = committee, user = member)))

        assertThat(committee.hasMember(member)).isTrue
        assertThat(committee.hasMember(outsider)).isFalse
        assertThat(committee.hasMember(null as User?)).isFalse
    }
}
