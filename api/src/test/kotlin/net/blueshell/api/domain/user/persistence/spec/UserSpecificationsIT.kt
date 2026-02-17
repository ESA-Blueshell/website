package net.blueshell.api.domain.user.persistence.spec

import net.blueshell.api.domain.user.application.query.UserQuery
import net.blueshell.api.domain.user.persistence.repository.UserRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class UserSpecificationsIT : UserTestSupport() {

    @Autowired
    private lateinit var users: UserRepository

    @Nested
    inner class HasAuthorityAtLeast {

        @Test
        fun `includes users whose role inherits member`() {
            val guest = createUserWithRole(Role.GUEST)
            val member = createUserWithRole(Role.MEMBER)
            val board = createUserWithRole(Role.BOARD)

            val result = users.findAll(UserSpecifications.hasAuthorityAtLeast(Role.MEMBER))

            assertThat(result.map { it.id }).contains(member.id, board.id)
            assertThat(result.map { it.id }).doesNotContain(guest.id)
        }
    }

    @Nested
    inner class HasMemberRole {

        @Test
        fun `filters users with explicit member role`() {
            val member = createUserWithRole(Role.MEMBER)
            val board = createUserWithRole(Role.BOARD)
            val guest = createUserWithRole(Role.GUEST)

            val result = users.findAll(UserSpecifications.hasMemberRole(true))

            assertThat(result.map { it.id }).contains(member.id)
            assertThat(result.map { it.id }).doesNotContain(board.id, guest.id)
        }

        @Test
        fun `filters users without explicit member role`() {
            val member = createUserWithRole(Role.MEMBER)
            val board = createUserWithRole(Role.BOARD)
            val guest = createUserWithRole(Role.GUEST)

            val result = users.findAll(UserSpecifications.hasMemberRole(false))

            assertThat(result.map { it.id }).contains(board.id, guest.id)
            assertThat(result.map { it.id }).doesNotContain(member.id)
        }
    }

    @Nested
    inner class FromQuery {

        @Test
        fun `applies isMember true filter`() {
            val member = createUserWithRole(Role.MEMBER)
            val guest = createUserWithRole(Role.GUEST)

            val result = users.findAll(UserSpecifications.fromQuery(UserQuery(isMember = true), user = null))

            assertThat(result.map { it.id }).contains(member.id)
            assertThat(result.map { it.id }).doesNotContain(guest.id)
        }

        @Test
        fun `returns all users when query has no filters`() {
            val first = createUserWithRole(Role.MEMBER)
            val second = createUserWithRole(Role.GUEST)

            val result = users.findAll(UserSpecifications.fromQuery(UserQuery(), user = null))

            assertThat(result.map { it.id }).contains(first.id, second.id)
        }
    }
}
