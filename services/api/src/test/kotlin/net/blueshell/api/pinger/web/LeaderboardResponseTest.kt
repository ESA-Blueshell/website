package net.blueshell.api.pinger.web

import net.blueshell.api.pinger.api.CombinedRecord
import net.blueshell.api.pinger.api.DiscordStanding
import net.blueshell.api.pinger.api.FastestStanding
import net.blueshell.api.pinger.api.HouseStanding
import net.blueshell.api.pinger.api.Leaderboard
import net.blueshell.api.pinger.api.MemberStanding
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class LeaderboardResponseTest {
    @Test
    fun `a Discord-identified member maps its tag and avatar and leaves username null`() {
        val board =
            Leaderboard(
                house = HouseStanding(label = "SiteCie", totalSent = 5_000, online = true, pps = 150),
                members =
                    listOf(
                        MemberStanding(
                            rank = 1,
                            memberId = 2,
                            totalSent = 900,
                            online = true,
                            pps = 90,
                            discord = DiscordStanding(tag = "Bob#2", avatarUrl = "https://cdn/2.png"),
                            username = null,
                        ),
                    ),
            )

        val response = LeaderboardResponse.from(board)

        assertThat(response.house)
            .isEqualTo(HouseLineResponse(label = "SiteCie", totalSent = 5_000, online = true, pps = 150, peakPps = 0, peakAt = null))
        assertThat(response.house?.pps).isEqualTo(150)
        val row = response.members.single()
        assertThat(row.discordTag).isEqualTo("Bob#2")
        assertThat(row.avatarUrl).isEqualTo("https://cdn/2.png")
        assertThat(row.username).isNull()
        assertThat(row.pps).isEqualTo(90)
        assertThat(row.rank).isEqualTo(1)
    }

    @Test
    fun `a username-identified member maps its username and leaves the Discord fields null`() {
        val board =
            Leaderboard(
                house = null,
                members =
                    listOf(
                        MemberStanding(
                            rank = 1,
                            memberId = 1,
                            totalSent = 100,
                            online = false,
                            pps = 0,
                            discord = null,
                            username = "ann",
                        ),
                    ),
            )

        val response = LeaderboardResponse.from(board)

        assertThat(response.house).isNull()
        val row = response.members.single()
        assertThat(row.username).isEqualTo("ann")
        assertThat(row.discordTag).isNull()
        assertThat(row.avatarUrl).isNull()
    }

    @Test
    fun `the fastest board, the record and the combined rate map across`() {
        val at = Instant.parse("2026-10-09T21:14:00Z")
        val board =
            Leaderboard(
                house = HouseStanding(label = "SiteCie", totalSent = 5_000, online = true, pps = 150, peakPps = 17_000, peakAt = at),
                members = emptyList(),
                fastest =
                    listOf(
                        FastestStanding(
                            rank = 1,
                            memberId = 2,
                            peakPps = 700,
                            peakAt = at,
                            discord = DiscordStanding(tag = "Bob#2", avatarUrl = "https://cdn/2.png"),
                            username = null,
                        ),
                    ),
                record = CombinedRecord(pps = 2_400_000, at = at),
                combinedPps = 9_300,
            )

        val response = LeaderboardResponse.from(board)

        assertThat(response.house?.peakPps).isEqualTo(17_000)
        assertThat(response.house?.peakAt).isEqualTo(at)
        assertThat(response.fastest.single())
            .isEqualTo(
                FastestResponse(
                    rank = 1,
                    memberId = 2,
                    peakPps = 700,
                    peakAt = at,
                    discordTag = "Bob#2",
                    avatarUrl = "https://cdn/2.png",
                    username = null,
                ),
            )
        assertThat(response.record).isEqualTo(RecordResponse(pps = 2_400_000, at = at))
        assertThat(response.record?.pps).isEqualTo(2_400_000)
        assertThat(response.fastest.single().peakPps).isEqualTo(700)
        assertThat(response.combinedPps).isEqualTo(9_300)
    }

    @Test
    fun `a board with no record maps to a null record`() {
        assertThat(LeaderboardResponse.from(Leaderboard(house = null, members = emptyList())).record).isNull()
    }
}
