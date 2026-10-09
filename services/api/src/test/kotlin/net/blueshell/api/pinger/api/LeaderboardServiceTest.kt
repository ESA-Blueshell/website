package net.blueshell.api.pinger.api

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.pinger.persistence.PingerContribution
import net.blueshell.api.pinger.persistence.PingerContributionRepository
import net.blueshell.api.pinger.persistence.PingerLive
import net.blueshell.api.pinger.persistence.PingerLiveStore
import net.blueshell.api.pinger.persistence.PingerRecord
import net.blueshell.api.pinger.persistence.PingerRecordRepository
import net.blueshell.api.shared.discord.DiscordFace
import net.blueshell.api.shared.discord.DiscordFaces
import net.blueshell.api.shared.user.MemberIdentities
import net.blueshell.api.shared.user.MemberIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional

class LeaderboardServiceTest {
    private val contributions = mockk<PingerContributionRepository>()
    private val live = mockk<PingerLiveStore>()
    private val records = mockk<PingerRecordRepository>()

    init {
        every { records.findById(1L) } returns Optional.of(PingerRecord())
    }

    private fun contribution(
        identity: String,
        memberId: Long?,
        total: Long,
        peak: Long = 0,
        peakAt: Instant? = null,
    ) = PingerContribution(
        identity = identity,
        memberId = memberId,
        updated = Instant.EPOCH,
        totalSent = total,
        peakPps = peak,
        peakAt = peakAt,
    )

    private fun service(
        identities: MemberIdentities = MemberIdentities { emptyMap() },
        faces: DiscordFaces = DiscordFaces { emptyMap() },
    ) = LeaderboardService(contributions, identities, faces, live, records)

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

    @Test
    fun `the fastest board ranks members by peak rate and leaves out who never sent online`() {
        val at = Instant.parse("2026-10-09T21:00:00Z")
        every { contributions.findAll() } returns
            listOf(
                contribution("member:1", 1, total = 900, peak = 200, peakAt = at),
                contribution("member:2", 2, total = 100, peak = 700, peakAt = at),
                contribution("member:3", 3, total = 500),
            )
        offline()
        val identities =
            MemberIdentities {
                mapOf(1L to MemberIdentity("ann", null), 2L to MemberIdentity("bob", discordId = "7"), 3L to MemberIdentity("cara", null))
            }
        val faces = DiscordFaces { mapOf("7" to DiscordFace(name = "Bob#7", avatar = "https://cdn/7.png")) }

        val fastest = service(identities = identities, faces = faces).snapshot().fastest

        assertThat(fastest.map { it.memberId }).containsExactly(2, 1)
        assertThat(fastest.map { it.rank }).containsExactly(1, 2)
        assertThat(fastest[0].peakPps).isEqualTo(700)
        assertThat(fastest[0].peakAt).isEqualTo(at)
        assertThat(fastest[0].discord).isEqualTo(DiscordStanding(tag = "Bob#7", avatarUrl = "https://cdn/7.png"))
        assertThat(fastest[0].username).isNull()
        assertThat(fastest[1].username).isEqualTo("ann")
    }

    @Test
    fun `the house line carries SiteCie's peak`() {
        val at = Instant.parse("2026-10-09T21:00:00Z")
        every { contributions.findAll() } returns listOf(contribution("sitecie", null, total = 5_000, peak = 17_000, peakAt = at))
        offline()

        val house = service().snapshot().house!!

        assertThat(house.peakPps).isEqualTo(17_000)
        assertThat(house.peakAt).isEqualTo(at)
    }

    @Test
    fun `the board carries the combined record and the combined rate of every online sender right now`() {
        val at = Instant.parse("2026-10-09T21:14:00Z")
        every { contributions.findAll() } returns emptyList()
        every { records.findById(1L) } returns Optional.of(PingerRecord(pps = 2_400_000, setAt = at))
        every { live.aggregateAll() } returns
            mapOf(
                "sitecie" to PingerLive(online = true, pps = 9_000, lastSeen = at),
                "member:1" to PingerLive(online = true, pps = 300, lastSeen = at),
                "member:2" to PingerLive(online = false, pps = 0, lastSeen = at),
            )

        val board = service().snapshot()

        assertThat(board.record).isEqualTo(CombinedRecord(pps = 2_400_000, at = at))
        assertThat(board.combinedPps).isEqualTo(9_300)
    }

    @Test
    fun `there is no record until one is set`() {
        every { contributions.findAll() } returns emptyList()
        offline()

        assertThat(service().snapshot().record).isNull()
    }
}
