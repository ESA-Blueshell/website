package net.blueshell.api.event.domain

import net.blueshell.api.event.persistence.EventSignUpRepository
import net.blueshell.api.event.persistence.RosterRow
import net.blueshell.api.shared.discord.DiscordFace
import net.blueshell.api.shared.discord.DiscordFaces
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class EventRosterTest {
    private fun row(
        username: String?,
        discordId: String? = null,
        discord: String? = null,
    ) = object : RosterRow {
        override val username = username
        override val discordId = discordId
        override val discord = discord
    }

    private val signUps =
        mock<EventSignUpRepository> {
            on { findRoster(7) } doReturn
                listOf(
                    row("nelly", "80351110224678912", "Nelly"),
                    row("mo", "111", "Mo"),
                    row("lars"),
                    row(null),
                    row("blank", " ", " "),
                    row(null),
                )
        }

    @Test
    fun `names each account by the Discord the bot sees, else as last known, else by username, and counts the guests`() {
        val faces = DiscordFaces { ids -> ids.filter { it == "111" }.associateWith { DiscordFace("Mo the Great", "https://cdn/mo.png") } }

        val roster = EventRoster(signUps, faces).of(7)

        assertThat(roster.people).containsExactly(
            RosterPerson("Nelly", "https://cdn.discordapp.com/embed/avatars/5.png", discord = true),
            RosterPerson("Mo the Great", "https://cdn/mo.png", discord = true),
            RosterPerson("lars", null, discord = false),
            RosterPerson("blank", null, discord = false),
        )
        assertThat(roster.guests).isEqualTo(2)
    }

    @Test
    fun `a linked account with no name known yet is named by username`() {
        val unnamed = mock<EventSignUpRepository> { on { findRoster(8) } doReturn listOf(row("ann", "222", null)) }

        assertThat(EventRoster(unnamed) { emptyMap() }.of(8).people).containsExactly(RosterPerson("ann", null, discord = false))
    }
}
