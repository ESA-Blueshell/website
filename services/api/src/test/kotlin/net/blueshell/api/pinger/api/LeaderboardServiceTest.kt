package net.blueshell.api.pinger.api

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.pinger.persistence.PingerContribution
import net.blueshell.api.pinger.persistence.PingerContributionRepository
import net.blueshell.api.pinger.persistence.PingerLive
import net.blueshell.api.pinger.persistence.PingerLiveStore
import net.blueshell.api.shared.discord.DiscordFace
import net.blueshell.api.shared.discord.DiscordFaces
import net.blueshell.api.shared.user.MemberIdentities
import net.blueshell.api.shared.user.MemberIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class LeaderboardServiceTest {
    private val contributions = mockk<PingerContributionRepository>()
    private val live = mockk<PingerLiveStore>()

    private fun contribution(
        identity: String,
        memberId: Long?,
        total: Long,
    ) = PingerContribution(identity = identity, memberId = memberId, updated = Instant.EPOCH, totalSent = total)

    private fun service(
        identities: MemberIdentities = MemberIdentities { emptyMap() },
        faces: DiscordFaces = DiscordFaces { emptyMap() },
    ) = LeaderboardService(contributions, identities, faces, live)

    private fun offline() {
        every { live.aggregateAll() } returns emptyMap()
    }

    @Test
    fun `every contributor appears, ranked by total sent descending`() {
        every { contributions.findAll() } returns
            listOf(
                contribution("member:1", 1, total = 100),
                contribution("member:2", 2, total = 300),
                contribution("member:3", 3, total = 200),
            )
        offline()
        val identities =
            MemberIdentities {
                mapOf(1L to MemberIdentity("ann", null), 2L to MemberIdentity("bob", null), 3L to MemberIdentity("cara", null))
            }

        val board = service(identities = identities).snapshot()

        assertThat(board.members.map { it.memberId }).containsExactly(2, 3, 1)
        assertThat(board.members.map { it.rank }).containsExactly(1, 2, 3)
        assertThat(board.members.map { it.username }).containsExactly("bob", "cara", "ann")
    }

    @Test
    fun `a member swapping total overtakes the other in the ranking`() {
        every { contributions.findAll() } returns
            listOf(
                contribution("member:1", 1, total = 500),
                contribution("member:2", 2, total = 900),
            )
        offline()
        val identities =
            MemberIdentities { mapOf(1L to MemberIdentity("ann", null), 2L to MemberIdentity("bob", null)) }

        val board = service(identities = identities).snapshot()

        assertThat(board.members.map { it.memberId }).containsExactly(2, 1)
    }

    @Test
    fun `a linked and visible member shows their Discord tag and avatar, not a username`() {
        every { contributions.findAll() } returns listOf(contribution("member:1", 1, total = 100))
        offline()
        val identities = MemberIdentities { mapOf(1L to MemberIdentity("ann", discordId = "42")) }
        val faces = DiscordFaces { mapOf("42" to DiscordFace(name = "Ann#1", avatar = "https://cdn/42.png")) }

        val row = service(identities = identities, faces = faces).snapshot().members.single()

        assertThat(row.discord).isEqualTo(DiscordStanding(tag = "Ann#1", avatarUrl = "https://cdn/42.png"))
        assertThat(row.username).isNull()
    }

    @Test
    fun `a member whose Discord the bot cannot see falls back to the site username`() {
        every { contributions.findAll() } returns listOf(contribution("member:1", 1, total = 100))
        offline()
        val identities = MemberIdentities { mapOf(1L to MemberIdentity("ann", discordId = "42")) }
        val faces = DiscordFaces { emptyMap() }

        val row = service(identities = identities, faces = faces).snapshot().members.single()

        assertThat(row.discord).isNull()
        assertThat(row.username).isEqualTo("ann")
    }

    @Test
    fun `SiteCie is a labelled house line outside the member ranking`() {
        every { contributions.findAll() } returns
            listOf(
                contribution("sitecie", null, total = 5_000),
                contribution("member:1", 1, total = 100),
            )
        every { live.aggregateAll() } returns mapOf("sitecie" to PingerLive(online = true, pps = 200, lastSeen = Instant.EPOCH))
        val identities = MemberIdentities { mapOf(1L to MemberIdentity("ann", null)) }

        val board = service(identities = identities).snapshot()

        assertThat(board.house).isEqualTo(HouseStanding(label = "SiteCie", totalSent = 5_000, online = true, pps = 200))
        assertThat(board.members.map { it.memberId }).containsExactly(1)
    }

    @Test
    fun `an online member is marked online and carries its aggregated rate from the live row`() {
        every { contributions.findAll() } returns listOf(contribution("member:1", 1, total = 100))
        every { live.aggregateAll() } returns mapOf("member:1" to PingerLive(online = true, pps = 64, lastSeen = Instant.EPOCH))
        val identities = MemberIdentities { mapOf(1L to MemberIdentity("ann", null)) }

        val row = service(identities = identities).snapshot().members.single()

        assertThat(row.online).isTrue()
        assertThat(row.pps).isEqualTo(64)
    }

    @Test
    fun `a contributor with no account is left out of the ranking`() {
        every { contributions.findAll() } returns listOf(contribution("member:9", 9, total = 100))
        offline()

        val board = service(identities = MemberIdentities { emptyMap() }).snapshot()

        assertThat(board.members).isEmpty()
    }

    @Test
    fun `a board with no SiteCie row has no house line`() {
        every { contributions.findAll() } returns listOf(contribution("member:1", 1, total = 100))
        offline()
        val identities = MemberIdentities { mapOf(1L to MemberIdentity("ann", null)) }

        assertThat(service(identities = identities).snapshot().house).isNull()
    }
}
