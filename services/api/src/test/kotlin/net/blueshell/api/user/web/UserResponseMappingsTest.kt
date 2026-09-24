package net.blueshell.api.user.web

import net.blueshell.api.user.persistence.DeletedUser
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class UserResponseMappingsTest {
    private val user =
        User(
            username = "nelly",
            email = "nelly@example.com",
            password = "encoded",
            initials = "N",
            firstName = "Nelly",
            lastName = "B",
            discord = "Nelly B",
            newsletter = false,
        ).apply {
            id = 7
            discordId = "1144058844004233369"
            createdAt = Instant.EPOCH
            updatedAt = Instant.EPOCH
        }

    @Test
    fun `hands back the linked Discord member beside the name, wherever an account is read`() {
        assertThat(user.asDetailResponse().discordId).isEqualTo("1144058844004233369")
        assertThat(user.asSummaryResponse().discordId).isEqualTo("1144058844004233369")

        val deleted =
            DeletedUser.fromUser(user, Instant.EPOCH, Instant.EPOCH.plusSeconds(60)).apply {
                createdAt = Instant.EPOCH
                updatedAt = Instant.EPOCH
            }
        assertThat(deleted.asDetailResponse().discordId).isEqualTo("1144058844004233369")
    }
}
