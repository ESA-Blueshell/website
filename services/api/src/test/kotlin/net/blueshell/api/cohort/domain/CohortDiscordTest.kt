package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.discord.api.DiscordChannelKeeper
import net.blueshell.api.discord.api.DiscordRefused
import net.blueshell.api.discord.api.DiscordRoleKeeper
import net.blueshell.api.discord.api.DiscordUnavailable
import net.blueshell.api.discord.api.KeptChannel
import net.blueshell.api.discord.api.KeptChannelKind
import net.blueshell.api.discord.api.KeptRole
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

class CohortDiscordTest {
    private val cohorts: CohortRepository = mock()
    private val targets: TargetRepository = mock()
    private val targetIds: CohortTargetIds = mock()
    private val targeting: CohortTargeting = mock()
    private val registrar: CohortRegistrar = mock()
    private val roles: DiscordRoleKeeper = mock()
    private val channels: DiscordChannelKeeper = mock()
    private val discord = CohortDiscord(cohorts, targets, targetIds, targeting, registrar, roles, channels)
    private val cohort = Entities.cohort(id = 5, type = CohortType.COMMITTEE_MEMBERS, label = "Sitecie")
    private val role = Entities.target(id = 50, system = "DISCORD", cohortId = 5, externalId = "900")
    private val sitecie = KeptChannel("1", "sitecie", KeptChannelKind.TEXT, "Committees")

    private fun given(linked: Boolean) {
        whenever(roles.available()).thenReturn(true)
        whenever(channels.available()).thenReturn(true)
        whenever(cohorts.findByDefinitionKey("COMMITTEE_MEMBERS:7")).thenReturn(cohort)
        whenever(cohorts.findById(5)).thenReturn(Optional.of(cohort))
        whenever(targets.findByCohortIdAndSystem(5, "DISCORD")).thenReturn(if (linked) role else null)
        whenever(targetIds.find(role)).thenReturn("900")
        whenever(roles.role("900")).thenReturn(KeptRole("900", "Sitecie", true))
    }

    @Test
    fun `reads the role the seats hold and the channels it opens, and nothing without a bot`() {
        given(linked = true)
        whenever(channels.openedTo("900")).thenReturn(listOf(sitecie))

        assertThat(discord.read("COMMITTEE_MEMBERS:7")).isEqualTo(DiscordPlace(true, "900", "Sitecie", listOf(sitecie)))

        whenever(roles.available()).thenReturn(false)
        assertThat(discord.read("COMMITTEE_MEMBERS:7")).isEqualTo(DiscordPlace(false, null, null, emptyList()))
        assertThatThrownBy {
            discord.apply("COMMITTEE_MEMBERS:7", DiscordChoice(createRole = true), "Committees")
        }.isInstanceOf(TargetSystemUnavailable::class.java)
    }

    @Test
    fun `a new committee by default gets a new role and a private channel under Committees`() {
        given(linked = false)
        whenever(targeting.create(5, TargetSystem.DISCORD, "Sitecie", null)).thenReturn(CohortTargetRow(role, "900"))
        whenever(channels.openedTo("900")).thenReturn(emptyList())

        discord.apply("COMMITTEE_MEMBERS:7", DiscordChoice(createRole = true, createChannel = " sitecie "), "Committees")

        verify(channels).createPrivate("sitecie", "Committees", "900")
    }

    @Test
    fun `links an existing role, opens the channels chosen and closes the ones left out, keeping a linked role`() {
        given(linked = false)
        whenever(targeting.linkExisting(5, TargetSystem.DISCORD, "901")).thenReturn(CohortTargetRow(role, "901"))
        val committees = KeptChannel("9", "Committees", KeptChannelKind.CATEGORY, null)
        whenever(channels.openedTo("901")).thenReturn(listOf(sitecie, committees))

        discord.apply("COMMITTEE_MEMBERS:7", DiscordChoice(roleId = "901", channelIds = listOf("2")), "Committees")

        verify(channels).open("2", "901", true)
        verify(channels).close("1", "901")
        // A category is never named on a form, so leaving it out takes nothing off the role.
        verify(channels, never()).close("9", "901")

        given(linked = true)
        whenever(channels.openedTo("900")).thenReturn(emptyList())
        discord.apply("COMMITTEE_MEMBERS:7", DiscordChoice(roleId = "902", createChannel = " "), "Committees")
        verify(targeting, never()).linkExisting(5, TargetSystem.DISCORD, "902")
        verify(channels, never()).createPrivate(any(), any(), any())
    }

    @Test
    fun `a role another cohort follows is refused naming that cohort, or taken from it when asked to move it`() {
        given(linked = false)
        val board = Entities.cohort(id = 6, type = CohortType.BOARD, label = "Board")
        val boardsRole = Entities.target(id = 60, system = "DISCORD", cohortId = 6, externalId = "901")
        whenever(targets.findFirstBySystemAndExternalId("DISCORD", "901")).thenReturn(boardsRole)
        whenever(cohorts.findById(6)).thenReturn(Optional.of(board))

        assertThatThrownBy { discord.apply("COMMITTEE_MEMBERS:7", DiscordChoice(roleId = "901"), "Committees") }
            .isInstanceOfSatisfying(TargetLinkedElsewhere::class.java) { assertThat(it.facts["cohort"]).isEqualTo("Board") }
        verify(targets, never()).delete(boardsRole)

        whenever(targeting.linkExisting(5, TargetSystem.DISCORD, "901")).thenReturn(CohortTargetRow(role, "901"))
        whenever(channels.openedTo("901")).thenReturn(emptyList())
        discord.apply("COMMITTEE_MEMBERS:7", DiscordChoice(roleId = "901", move = true), "Committees")
        verify(targets).delete(boardsRole)
        verify(targeting).linkExisting(5, TargetSystem.DISCORD, "901")

        // A target without a cohort is named by its own label, and the cohort's own role is no conflict.
        val orphan = Entities.target(id = 61, system = "DISCORD", label = "Orphan")
        whenever(targets.findFirstBySystemAndExternalId("DISCORD", "903")).thenReturn(orphan)
        assertThatThrownBy { discord.apply("COMMITTEE_MEMBERS:7", DiscordChoice(roleId = "903"), "Committees") }
            .isInstanceOfSatisfying(TargetLinkedElsewhere::class.java) { assertThat(it.facts["cohort"]).isEqualTo("Orphan") }
        val own = Entities.target(id = 62, system = "DISCORD", cohortId = 5)
        whenever(targets.findFirstBySystemAndExternalId("DISCORD", "904")).thenReturn(own)
        whenever(targeting.linkExisting(5, TargetSystem.DISCORD, "904")).thenReturn(CohortTargetRow(role, "904"))
        whenever(channels.openedTo("904")).thenReturn(emptyList())
        discord.apply("COMMITTEE_MEMBERS:7", DiscordChoice(roleId = "904"), "Committees")
        verify(targeting).linkExisting(5, TargetSystem.DISCORD, "904")
    }

    @Test
    fun `the board's committee is refused a role, since the board in office holds it`() {
        given(linked = false)
        val boardsCommittee = Entities.cohort(id = 5, type = CohortType.COMMITTEE_MEMBERS, label = "board")
        whenever(cohorts.findById(5)).thenReturn(Optional.of(boardsCommittee))

        assertThatThrownBy { discord.apply("COMMITTEE_MEMBERS:7", DiscordChoice(createRole = true), "Committees") }
            .isInstanceOf(BoardCommitteeHasNoRole::class.java)
        assertThatThrownBy { discord.apply("COMMITTEE_MEMBERS:7", DiscordChoice(roleId = "901"), "Committees") }
            .isInstanceOf(BoardCommitteeHasNoRole::class.java)
        verify(targeting, never()).create(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `a role or a channel already in the server by name is linked rather than made again`() {
        given(linked = false)
        whenever(roles.roles()).thenReturn(listOf(KeptRole("905", "SiteCie", true)))
        whenever(targets.findAllBySystem("DISCORD")).thenReturn(emptyList())
        whenever(targeting.linkExisting(5, TargetSystem.DISCORD, "905")).thenReturn(CohortTargetRow(role, "905"))
        whenever(channels.openedTo("905")).thenReturn(emptyList())
        whenever(channels.channels()).thenReturn(listOf(sitecie, KeptChannel("3", "site-cie", KeptChannelKind.VOICE, null)))

        discord.apply("COMMITTEE_MEMBERS:7", DiscordChoice(createRole = true, createChannel = "#Site-Cie"), "Committees")

        verify(targeting).linkExisting(5, TargetSystem.DISCORD, "905")
        verify(targeting, never()).create(any(), any(), any(), anyOrNull())
        verify(channels).open("1", "905", true)
        verify(channels, never()).createPrivate(any(), any(), any())

        // Run again with the channel already open to the role: nothing more is done.
        given(linked = true)
        whenever(channels.openedTo("900")).thenReturn(listOf(sitecie))
        discord.apply("COMMITTEE_MEMBERS:7", DiscordChoice(channelIds = listOf("1"), createChannel = "sitecie"), "Committees")
        verify(channels, never()).open("1", "900", true)
        verify(channels, never()).createPrivate(any(), any(), any())
    }

    @Test
    fun `without a role nothing is opened, a cohort not yet registered is registered first, and a bot gone mid-call refuses`() {
        given(linked = false)
        assertThat(discord.apply("COMMITTEE_MEMBERS:7", DiscordChoice(channelIds = listOf("2")), "Committees").roleId).isNull()
        verify(channels, never()).open(any(), any(), any())

        whenever(cohorts.findByDefinitionKey("COMMITTEE_MEMBERS:8")).thenReturn(null, cohort)
        discord.read("COMMITTEE_MEMBERS:8")
        verify(registrar).register()
        whenever(cohorts.findByDefinitionKey("COMMITTEE_MEMBERS:9")).thenReturn(null)
        assertThatThrownBy { discord.read("COMMITTEE_MEMBERS:9") }.isInstanceOf(ResponseStatusException::class.java)

        given(linked = true)
        whenever(channels.openedTo("900")).thenThrow(DiscordUnavailable("gone"))
        assertThatThrownBy { discord.read("COMMITTEE_MEMBERS:7") }.isInstanceOf(TargetSystemUnavailable::class.java)
    }

    @Test
    fun `unlinking takes the role off its cohort and asks nothing of Discord`() {
        whenever(targets.findAllBySystem("DISCORD")).thenReturn(listOf(role))
        whenever(targetIds.find(role)).thenReturn("900")

        discord.unlink("999")
        verify(targets, never()).delete(role)
        discord.unlink("900")

        verify(targets).delete(role)
        verifyNoInteractions(roles, channels)
    }

    @Test
    fun `a change Discord will not let the bot make is refused with Discord's reason`() {
        given(linked = true)
        whenever(channels.openedTo("900")).thenReturn(emptyList())
        whenever(channels.open("2", "900", true)).thenThrow(DiscordRefused("The bot may not change lancie: it lacks View Channels there."))

        assertThatThrownBy { discord.apply("COMMITTEE_MEMBERS:7", DiscordChoice(channelIds = listOf("2")), "Committees") }
            .isInstanceOfSatisfying(TargetSystemRefused::class.java) { assertThat(it.reason).contains("lacks View Channels") }
    }

    @Test
    fun `removes the role and the private channels it opens, and unlinks it, only where asked`() {
        given(linked = true)
        val category = KeptChannel("10", "Committees", KeptChannelKind.CATEGORY, null)
        whenever(channels.openedTo("900")).thenReturn(listOf(sitecie, category))

        discord.remove("COMMITTEE_MEMBERS:7")

        verify(channels).delete("1")
        verify(channels, never()).delete("10")
        verify(roles).delete("900")
        verify(targets).delete(role)

        given(linked = false)
        discord.remove("COMMITTEE_MEMBERS:7")
        whenever(roles.available()).thenReturn(false)
        assertThatThrownBy { discord.remove("COMMITTEE_MEMBERS:7") }.isInstanceOf(TargetSystemUnavailable::class.java)
    }

    @Test
    fun `archiving moves the channels the role opens into the archive and back, and nothing without a bot or a role`() {
        given(linked = true)
        val category = KeptChannel("10", "Committees", KeptChannelKind.CATEGORY, null)
        whenever(channels.openedTo("900")).thenReturn(listOf(sitecie, category))

        discord.archive("COMMITTEE_MEMBERS:7", true)
        discord.archive("COMMITTEE_MEMBERS:7", false)
        verify(channels).archive(listOf("1"))
        verify(channels).restore(listOf("1"))

        whenever(cohorts.findByDefinitionKey("COMMITTEE_MEMBERS:8")).thenReturn(null)
        discord.archive("COMMITTEE_MEMBERS:8", true)
        given(linked = false)
        discord.archive("COMMITTEE_MEMBERS:7", true)
        whenever(roles.available()).thenReturn(false)
        discord.archive("COMMITTEE_MEMBERS:7", true)
        verify(channels, org.mockito.kotlin.times(1)).archive(any())
    }
}
