package net.blueshell.api.committee.domain

import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.committee.persistence.CommitteeMember
import net.blueshell.api.shared.discord.DiscordFace
import net.blueshell.api.shared.discord.DiscordFaces
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CommitteeSeatsTest {
    private fun user(
        discordId: String?,
        discord: String?,
    ) = Entities.user().also {
        it.discordId = discordId
        it.discord = discord
    }

    private val committee =
        Committee(name = "LanCie", description = "LANs").apply {
            replaceMembers(
                listOf(
                    CommitteeMember(committee = this, user = user("80351110224678912", "Nelly"), role = "Chair"),
                    CommitteeMember(committee = this, user = user("111", "Mo"), role = null),
                    CommitteeMember(committee = this, user = user(null, "Typed name"), role = "Treasurer"),
                ),
            )
        }

    @Test
    fun `names each seat by the Discord the bot sees, else as last known, else not at all`() {
        val faces = DiscordFaces { ids -> ids.filter { it == "111" }.associateWith { DiscordFace("Mo the Great", "https://cdn/mo.png") } }

        val seats = CommitteeSeats(faces).of(committee)

        assertThat(seats).containsExactly(
            CommitteeSeat("Nelly", "https://cdn.discordapp.com/embed/avatars/5.png", "Chair"),
            CommitteeSeat("Mo the Great", "https://cdn/mo.png", null),
            CommitteeSeat(null, null, "Treasurer"),
        )
    }

    @Test
    fun `without a bot every linked seat keeps its last known name and Discord's default avatar`() {
        val seats = CommitteeSeats { emptyMap() }.of(committee)

        assertThat(seats.map { it.discordName }).containsExactly("Nelly", "Mo", null)
        assertThat(seats[1].avatar).startsWith("https://cdn.discordapp.com/embed/avatars/")
    }
}
