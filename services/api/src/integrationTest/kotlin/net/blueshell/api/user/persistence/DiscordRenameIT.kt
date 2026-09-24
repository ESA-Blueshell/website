package net.blueshell.api.user.persistence

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class DiscordRenameIT : UserTestSupport() {
    @Test
    fun `renames the account linked to the member, and nobody else`() {
        val linked = userRepository.save(createUserWithRole(Role.MEMBER).apply { discordId = "1144058844004233369" })
        val other = createUserWithRole(Role.MEMBER)
        val version = linked.version

        val renamed = transactionTemplate.execute { userRepository.renameDiscordMember("1144058844004233369", "Nelly B") }

        assertThat(renamed).isEqualTo(1)
        assertThat(userRepository.findById(linked.id!!).get().discord).isEqualTo("Nelly B")
        assertThat(userRepository.findById(linked.id!!).get().version).isEqualTo(version)
        assertThat(userRepository.findById(other.id!!).get().discord).isEqualTo(other.discord)
    }

    @Test
    fun `leaves a name that has not changed alone`() {
        userRepository.save(createUserWithRole(Role.MEMBER).apply { discordId = "1144058844004233370"; discord = "Anna" })

        val renamed = transactionTemplate.execute { userRepository.renameDiscordMember("1144058844004233370", "Anna") }

        assertThat(renamed).isZero()
    }
}
