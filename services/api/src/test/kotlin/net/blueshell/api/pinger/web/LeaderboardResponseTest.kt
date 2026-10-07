package net.blueshell.api.pinger.web

import net.blueshell.api.pinger.api.DiscordStanding
import net.blueshell.api.pinger.api.HouseStanding
import net.blueshell.api.pinger.api.Leaderboard
import net.blueshell.api.pinger.api.MemberStanding
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LeaderboardResponseTest {
    @Test
    fun `a Discord-identified member maps its tag and avatar and leaves username null`() {
        val board =
            Leaderboard(
                house = HouseStanding(label = "SiteCie", totalSent = 5_000, online = true),
                members =
                    listOf(
                        MemberStanding(
                            rank = 1,
                            memberId = 2,
                            totalSent = 900,
                            online = true,
                            discord = DiscordStanding(tag = "Bob#2", avatarUrl = "https://cdn/2.png"),
                            username = null,
                        ),
                    ),
            )

        val response = LeaderboardResponse.from(board)

        assertThat(response.house).isEqualTo(HouseLineResponse(label = "SiteCie", totalSent = 5_000, online = true))
        val row = response.members.single()
        assertThat(row.discordTag).isEqualTo("Bob#2")
        assertThat(row.avatarUrl).isEqualTo("https://cdn/2.png")
        assertThat(row.username).isNull()
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
}
