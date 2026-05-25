package net.blueshell.api.domain.user.application.listener

import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.committee.persistence.CommitteeMember
import net.blueshell.api.domain.committee.persistence.repository.CommitteeMemberRepository
import net.blueshell.api.domain.user.application.event.UserUpdated
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder

class UserEventListenerTest : ServiceTestSupport() {

    @Autowired
    private lateinit var listener: UserEventListener

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var committeeMemberRepository: CommitteeMemberRepository

    @Test
    fun `removes committee memberships when the user no longer has the MEMBER role`() {
        val user = createAndSaveUser("former_member", "former@example.com")
        val committee = persist(Committee(name = "Test Committee ${System.currentTimeMillis()}", description = "x"))
        val membership = persist(CommitteeMember(user = user, committee = committee))

        user.roles = mutableSetOf()
        persist(user)

        listener.onUpdate(UserUpdated(user.id!!))

        assertThat(committeeMemberRepository.findById(membership.id!!))
            .describedAs("Committee membership should be deleted")
            .isEmpty
    }

    private fun createAndSaveUser(username: String, email: String): User {
        val user = User(
            username = username,
            email = email,
            password = requireNotNull(passwordEncoder.encode("Password123!")) { "PasswordEncoder returned null hash" },
            initials = "TU",
            firstName = "Test",
            lastName = "User",
            phoneNumber = "06${System.currentTimeMillis().toString().takeLast(8)}",
            discord = "$username#0001"
        )
        user.enabled = true
        user.roles = mutableSetOf(Role.MEMBER)
        return persist(user)
    }
}
