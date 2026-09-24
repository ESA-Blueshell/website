package net.blueshell.api.user.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserRequestMappingsTest {
    @Test
    fun `carries the picked Discord member through a self edit and a board edit`() {
        val self = UpdateUserRequest(newsletter = true, discord = "Nelly B", discordId = "803", phoneNumber = "0612345678", version = 1)
        val board =
            BoardUpdateUserRequest(
                username = "nelly",
                initials = "N",
                firstName = "Nelly",
                lastName = "B",
                email = "nelly@example.com",
                newsletter = true,
                discord = "Nelly B",
                discordId = "803",
                phoneNumber = "0612345678",
                version = 1,
            )

        assertThat(self.asData().discordId).isEqualTo("803")
        assertThat(board.asBoardData().discordId).isEqualTo("803")
        assertThat(board.discordId).isEqualTo("803")

        // A board edit from before the picker sends no member, and links none.
        val unlinked =
            BoardUpdateUserRequest(
                username = "nelly",
                initials = "N",
                firstName = "Nelly",
                lastName = "B",
                email = "nelly@example.com",
                newsletter = true,
                discord = "Nelly B",
                phoneNumber = "0612345678",
                version = 1,
            )
        assertThat(unlinked.asBoardData().discordId).isNull()
    }
}
