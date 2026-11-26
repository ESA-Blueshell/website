package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordRoleKeeper
import net.blueshell.api.discord.api.KeptChannel
import net.blueshell.api.discord.api.KeptChannelKind
import net.blueshell.api.discord.api.KeptRole
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class DiscordAdoptionTest {
    private val cohorts: CohortRepository = mock()
    private val targets: TargetRepository = mock()
    private val targetIds: CohortTargetIds = mock()
    private val roles: DiscordRoleKeeper = mock()
    private val channels: DiscordChannelKeeper = mock()
    private val discord: CohortDiscord = mock()
    private val definitions: CohortDefinitionRegistry = mock()
    private val adoption = DiscordAdoption(cohorts, targets, targetIds, roles, channels, discord, definitions, "Committees", "Esports")

    private val sitecie = Entities.cohort(id = 1, label = "Sitecie").also { it.definitionKey = "COMMITTEE_MEMBERS:1" }
    private val valorant =
        Entities.cohort(id = 2, type = CohortType.TEAM_PLAYERS, label = "Blueshell Valorant").also { it.definitionKey = "TEAM_PLAYERS:2" }
    private val linked = Entities.cohort(id = 3, label = "Lancie").also { it.definitionKey = "COMMITTEE_MEMBERS:3" }
    private val twice = Entities.cohort(id = 4, label = "Nintenco").also { it.definitionKey = "COMMITTEE_MEMBERS:4" }
    private val members =
        Entities.cohort(id = 5, type = CohortType.CURRENT_MEMBERS, label = "Member").also { it.definitionKey = "CURRENT_MEMBERS" }
    private val boardsCommittee = Entities.cohort(id = 6, label = "Board").also { it.definitionKey = "COMMITTEE_MEMBERS:6" }
    private val above = Entities.cohort(id = 7, label = "Moderators").also { it.definitionKey = "COMMITTEE_MEMBERS:7" }

    // Disbanded: its cohort is left, with no definition behind it.
    private val disbanded = Entities.cohort(id = 8, label = "LAN Party").also { it.definitionKey = "COMMITTEE_MEMBERS:8" }

    // A committee and a team called the same.
    private val chessCie = Entities.cohort(id = 9, label = "Chess").also { it.definitionKey = "COMMITTEE_MEMBERS:9" }
    private val chessTeam =
        Entities.cohort(id = 10, type = CohortType.TEAM_PLAYERS, label = "Chess").also { it.definitionKey = "TEAM_PLAYERS:10" }
    private val sitecieText = KeptChannel("11", "sitecie", KeptChannelKind.TEXT, "Committees")
    private val valorantText = KeptChannel("12", "blueshell-valorant", KeptChannelKind.TEXT, "Esports")

    private fun given() {
        whenever(roles.available()).thenReturn(true)
        whenever(channels.available()).thenReturn(true)
        val all = listOf(valorant, sitecie, linked, twice, members, boardsCommittee, above, disbanded, chessCie, chessTeam)
        whenever(cohorts.findAll()).thenReturn(all)
        val defined = listOf(valorant, sitecie, linked, twice, members, boardsCommittee, above, chessCie, chessTeam)
        // Built before the stub: a mock made inside another's stubbing leaves it unfinished.
        val definitionsNow =
            defined.map { cohort ->
                org.mockito.kotlin.mock<CohortDefinition> {
                    on { key } doReturn cohort.definitionKey!!
                    on { type } doReturn cohort.type
                }
            }
        whenever(definitions.all()).thenReturn(definitionsNow)
        val lancieRole = Entities.target(id = 30, system = "DISCORD", cohortId = 3, externalId = "903")
        whenever(targets.findAllBySystem("DISCORD")).thenReturn(listOf(lancieRole))
        whenever(targetIds.find(lancieRole)).thenReturn("903")
        whenever(targets.findByCohortIdAndSystem(3, "DISCORD")).thenReturn(lancieRole)
        whenever(roles.roles()).thenReturn(
            listOf(
                KeptRole("901", "Sitecie", true),
                KeptRole("902", "Blueshell Valorant", true),
                KeptRole("903", "Lancie", true),
                KeptRole("904", "Nintenco", true),
                KeptRole("905", "nintenco", true),
                KeptRole("906", "Member", true),
                // The board's committee is never matched, so a role named Board stays free for the board.
                KeptRole("907", "Board", true),
                // Above the bot's own, so it could not be handed out.
                KeptRole("908", "Moderators", false),
                KeptRole("909", "LAN Party", true),
                KeptRole("910", "Chess", true),
            ),
        )
        whenever(channels.channels()).thenReturn(
            listOf(
                KeptChannel("10", "Sitecie", KeptChannelKind.CATEGORY, null),
                sitecieText,
                // Called as the committee, but not under Committees, so not offered.
                KeptChannel("13", "sitecie", KeptChannelKind.TEXT, "Board"),
                valorantText,
            ),
        )
    }

    @Test
    fun `matches committees and teams with no role to the one free role and the channel under their category named as they are`() {
        given()

        assertThat(adoption.proposals()).containsExactly(
            AdoptionMatch(
                "TEAM_PLAYERS:2",
                "Blueshell Valorant",
                CohortType.TEAM_PLAYERS,
                "902",
                "Blueshell Valorant",
                listOf(valorantText),
            ),
            AdoptionMatch("COMMITTEE_MEMBERS:1", "Sitecie", CohortType.COMMITTEE_MEMBERS, "901", "Sitecie", listOf(sitecieText)),
        )
    }

    private fun linkedAfter(vararg keys: String) {
        val cohortsByKey = listOf(sitecie, valorant).associateBy { it.definitionKey }
        cohortsByKey.forEach { (key, cohort) ->
            whenever(cohorts.findByDefinitionKey(key!!)).thenReturn(cohort)
            val role = Entities.target(id = 100 + cohort.id!!, system = "DISCORD", cohortId = cohort.id)
            // No role while the matches are read; the role once linked, where it was.
            whenever(targets.findByCohortIdAndSystem(cohort.id!!, "DISCORD")).thenReturn(null, if (key in keys) role else null)
            whenever(targetIds.find(role)).thenReturn("r${cohort.id}")
        }
    }

    private val sitecieSeen = AdoptionChoice("COMMITTEE_MEMBERS:1", "901", listOf("11"))
    private val valorantSeen = AdoptionChoice("TEAM_PLAYERS:2", "902", listOf("12"))

    @Test
    fun `links only the confirmed matches through the committee and team rules, each under its own category`() {
        given()
        linkedAfter("COMMITTEE_MEMBERS:1", "TEAM_PLAYERS:2")

        val outcome = adoption.adopt(listOf(sitecieSeen, valorantSeen, AdoptionChoice("COMMITTEE_MEMBERS:3", "903", emptyList())))

        assertThat(outcome).isEqualTo(AdoptionOutcome(2, emptyList()))

        verify(discord).apply("COMMITTEE_MEMBERS:1", DiscordChoice(roleId = "901", channelIds = listOf("11")), "Committees")
        verify(discord).apply("TEAM_PLAYERS:2", DiscordChoice(roleId = "902", channelIds = listOf("12")), "Esports")
        verify(discord, never()).apply(eq("COMMITTEE_MEMBERS:3"), any(), any())
    }

    @Test
    fun `a match the site refuses is named with its refusal, the others are still linked, and one linked before its channel counts`() {
        given()
        whenever(discord.apply(eq("COMMITTEE_MEMBERS:1"), any(), any()))
            .thenThrow(TargetSystemRefused(TargetSystem.DISCORD, "The bot may not change sitecie."))
        whenever(discord.apply(eq("TEAM_PLAYERS:2"), any(), any()))
            .thenThrow(ResponseStatusException(HttpStatus.CONFLICT, "Already linked"))
        // The committee's role went through before its channel was refused.
        linkedAfter("COMMITTEE_MEMBERS:1")

        val outcome = adoption.adopt(listOf(sitecieSeen, valorantSeen))

        assertThat(outcome).isEqualTo(
            AdoptionOutcome(
                1,
                listOf(
                    RefusedMatch("Blueshell Valorant", null, mapOf("reason" to "Already linked")),
                    RefusedMatch(
                        "Sitecie",
                        "TargetSystemRefused",
                        mapOf("system" to "Discord", "reason" to "The bot may not change sitecie."),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `a match no longer what the board saw is not linked`() {
        given()

        val outcome = adoption.adopt(listOf(sitecieSeen.copy(roleId = "999"), valorantSeen.copy(channelIds = emptyList())))

        assertThat(outcome).isEqualTo(AdoptionOutcome(0, emptyList()))
        verify(discord, never()).apply(any(), any(), any())
    }

    @Test
    fun `proposes nothing without a bot`() {
        assertThat(adoption.proposals()).isEmpty()
        verifyNoInteractions(cohorts, definitions)
    }
}
