package net.blueshell.api.pinger.web

import net.blueshell.api.pinger.persistence.PingerContribution
import net.blueshell.api.pinger.persistence.PingerContributionRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

/**
 * The public leaderboard reads the final tallies. Every contributor appears: contributing was the
 * permission, so there is no opt-in. The association has no bot token under test, so every member
 * resolves to their site username rather than a Discord face; the Discord-tag path is covered by
 * [net.blueshell.api.pinger.api.LeaderboardServiceTest].
 */
@SpringBootTest
class LeaderboardIT : UserTestSupport() {
    @Autowired
    private lateinit var contributions: PingerContributionRepository

    private fun tally(
        identity: String,
        memberId: Long?,
        total: Long,
    ) {
        contributions.save(
            PingerContribution(identity = identity, memberId = memberId, updated = Instant.now(), totalSent = total),
        )
    }

    @Test
    fun `every contributor appears and a member resolves to their site username`() {
        val top = createUserWithRole(Role.MEMBER)
        val next = createUserWithRole(Role.MEMBER)
        tally("member:${top.id}", top.id, total = 900)
        tally("member:${next.id}", next.id, total = 300)

        mvc
            .perform(get("/pinger/leaderboard"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.members.length()").value(2))
            .andExpect(jsonPath("$.members[0].memberId").value(top.id))
            .andExpect(jsonPath("$.members[0].rank").value(1))
            .andExpect(jsonPath("$.members[0].username").value(top.username))
            .andExpect(jsonPath("$.members[0].discordTag").doesNotExist())
            .andExpect(jsonPath("$.members[1].memberId").value(next.id))
    }

    @Test
    fun `SiteCie is a labelled house line outside the member ranking`() {
        val member = createUserWithRole(Role.MEMBER)
        tally("sitecie", null, total = 5_000)
        tally("member:${member.id}", member.id, total = 100)

        mvc
            .perform(get("/pinger/leaderboard"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.house.label").value("SiteCie"))
            .andExpect(jsonPath("$.house.totalSent").value(5_000))
            .andExpect(jsonPath("$.members.length()").value(1))
            .andExpect(jsonPath("$.members[0].memberId").value(member.id))
    }

    @Test
    fun `the board is readable without signing in`() {
        mvc.perform(get("/pinger/leaderboard")).andExpect(status().isOk)
    }
}
