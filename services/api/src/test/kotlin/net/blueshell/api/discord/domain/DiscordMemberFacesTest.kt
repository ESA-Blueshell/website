package net.blueshell.api.discord.domain

import net.blueshell.api.shared.discord.DiscordFace
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

class DiscordMemberFacesTest {
    @Test
    fun `answers the members asked for by username and avatar, and nobody the bot cannot see`() {
        val directory =
            mock<DiscordMemberDirectory> {
                on { everyoneKept() } doReturn
                    listOf(DiscordMember("1", "Nelly B", "nelly", "https://cdn/n.png"), DiscordMember("2", "Mo", "mo", "m"))
            }

        assertThat(DiscordMemberFaces(directory).of(listOf("1", "9"))).isEqualTo(mapOf("1" to DiscordFace("nelly", "https://cdn/n.png")))
    }

    @Test
    fun `asks nothing for nobody, and answers nobody without a bot`() {
        val quiet = mock<DiscordMemberDirectory>()
        assertThat(DiscordMemberFaces(quiet).of(emptyList())).isEmpty()
        verifyNoInteractions(quiet)

        assertThat(DiscordMemberFaces(mock { on { everyoneKept() } doReturn null }).of(listOf("1"))).isEmpty()
    }
}
