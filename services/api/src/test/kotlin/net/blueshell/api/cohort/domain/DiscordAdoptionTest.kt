package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordRoleKeeper
import net.blueshell.api.discord.api.KeptChannel
import net.blueshell.api.discord.api.KeptChannelKind
import net.blueshell.api.discord.api.KeptRole
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class DiscordAdoptionTest {
    private val cohorts: CohortRepository = mock()
    private val targets: TargetRepository = mock()
    private val roles: DiscordRoleKeeper = mock()
    private val channels: DiscordChannelKeeper = mock()
    private val discord: CohortDiscord = mock()
    private val adoption = DiscordAdoption(cohorts, targets, CohortTargetIds(targets), roles, channels, discord)

    private val sitecie = Entities.cohort(id = 1, label = "Sitecie").also { it.definitionKey = "COMMITTEE_MEMBERS:1" }
    private val valorant =
        Entities.cohort(id = 2, type = CohortType.TEAM_PLAYERS, label = "Blueshell Valorant").also { it.definitionKey = "TEAM_PLAYERS:2" }
    private val linked = Entities.cohort(id = 3, label = "Lancie").also { it.definitionKey = "COMMITTEE_MEMBERS:3" }
    private val twice = Entities.cohort(id = 4, label = "Nintenco").also { it.definitionKey = "COMMITTEE_MEMBERS:4" }
    private val members =
        Entities.cohort(id = 5, type = CohortType.CURRENT_MEMBERS, label = "Member").also {
            it.definitionKey =
                "CURRENT_MEMBERS"
        }
    private val sitecieText = KeptChannel("11", "sitecie", KeptChannelKind.TEXT, "Committees")

    private fun given() {
        whenever(roles.available()).thenReturn(true)
        whenever(channels.available()).thenReturn(true)
        whenever(cohorts.findAll()).thenReturn(listOf(valorant, sitecie, linked, twice, members))
        whenever(
            targets.findAllBySystem("DISCORD"),
        ).thenReturn(listOf(Entities.target(id = 30, system = "DISCORD", cohortId = 3, externalId = "903")))
        whenever(
            targets.findByCohortIdAndSystem(3, "DISCORD"),
        ).thenReturn(Entities.target(id = 30, system = "DISCORD", cohortId = 3, externalId = "903"))
        whenever(roles.roles()).thenReturn(
            listOf(
                KeptRole("901", "Sitecie", true),
                KeptRole("902", "Blueshell Valorant", true),
                KeptRole("903", "Lancie", true),
                KeptRole("904", "Nintenco", true),
                KeptRole("905", "nintenco", true),
                KeptRole("906", "Member", true),
            ),
        )
        whenever(channels.channels()).thenReturn(
            listOf(
                KeptChannel("10", "Sitecie", KeptChannelKind.CATEGORY, null),
                sitecieText,
                KeptChannel("12", "blueshell-valorant", KeptChannelKind.TEXT, "Esports"),
            ),
        )
    }

    @Test
    fun `matches committees and teams with no role to the free role and text channels named as they are`() {
        given()

        assertThat(adoption.proposals()).containsExactly(
            AdoptionMatch(
                "TEAM_PLAYERS:2",
                "Blueshell Valorant",
                CohortType.TEAM_PLAYERS,
                "902",
                "Blueshell Valorant",
                listOf(KeptChannel("12", "blueshell-valorant", KeptChannelKind.TEXT, "Esports")),
            ),
            AdoptionMatch("COMMITTEE_MEMBERS:1", "Sitecie", CohortType.COMMITTEE_MEMBERS, "901", "Sitecie", listOf(sitecieText)),
        )
    }

    @Test
    fun `links only the confirmed matches, keeping the channels the role opens already`() {
        given()
        whenever(channels.openedTo("901")).thenReturn(
            listOf(
                KeptChannel("20", "Committees", KeptChannelKind.CATEGORY, null),
                KeptChannel("21", "sitecie-voice", KeptChannelKind.VOICE, "Committees"),
                sitecieText,
            ),
        )

        assertThat(adoption.adopt(listOf("COMMITTEE_MEMBERS:1", "COMMITTEE_MEMBERS:3"))).isEqualTo(AdoptionOutcome(1, emptyList()))

        verify(discord).apply("COMMITTEE_MEMBERS:1", DiscordChoice(roleId = "901", channelIds = listOf("21", "11")), "")
    }

    @Test
    fun `a match Discord refuses is named with why, and the others are still linked`() {
        given()
        whenever(channels.openedTo(org.mockito.kotlin.any())).thenReturn(emptyList())
        whenever(discord.apply(org.mockito.kotlin.eq("COMMITTEE_MEMBERS:1"), org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenThrow(TargetSystemRefused(net.blueshell.api.shared.enums.TargetSystem.DISCORD, "The bot may not change sitecie."))

        val outcome = adoption.adopt(listOf("COMMITTEE_MEMBERS:1", "TEAM_PLAYERS:2"))

        assertThat(outcome).isEqualTo(AdoptionOutcome(1, listOf(RefusedMatch("Sitecie", "The bot may not change sitecie."))))
        verify(discord).apply(org.mockito.kotlin.eq("TEAM_PLAYERS:2"), org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `proposes nothing without a bot`() {
        assertThat(adoption.proposals()).isEmpty()
        whenever(roles.available()).thenReturn(true)
        assertThat(adoption.proposals()).isEmpty()
        verifyNoInteractions(cohorts, targets)
    }
}
