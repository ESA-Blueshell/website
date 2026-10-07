package net.blueshell.api.pinger.web

import net.blueshell.api.pinger.persistence.PingerBoardMember
import net.blueshell.api.pinger.persistence.PingerBoardMemberRepository
import net.blueshell.api.pinger.persistence.PingerContribution
import net.blueshell.api.pinger.persistence.PingerContributionRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

/**
 * The public leaderboard reads the durable tallies and the opt-in rows. The association has no bot
 * token under test, so every member resolves to their site username rather than a Discord face;
 * the Discord-tag path is covered by [net.blueshell.api.pinger.api.LeaderboardServiceTest].
 */
@SpringBootTest
class LeaderboardIT : UserTestSupport() {
    @Autowired
    private lateinit var contributions: PingerContributionRepository

    @Autowired
    private lateinit var optIns: PingerBoardMemberRepository

    private fun tally(
        identity: String,
        memberId: Long?,
        total: Long,
    ) {
        contributions.save(
            PingerContribution(identity = identity, memberId = memberId, updated = Instant.now(), totalSent = total),
        )
    }

    private fun optIn(
        member: User,
        optedIn: Boolean,
    ) {
        val row =
            optIns.findByMemberId(member.id!!)
                ?: PingerBoardMember(memberId = member.id!!, optedIn = optedIn, updated = Instant.now())
        row.optedIn = optedIn
        optIns.save(row)
    }

    @Test
    fun `only opted-in members appear and a member resolves to their site username`() {
        val shown = createUserWithRole(Role.MEMBER)
        val hidden = createUserWithRole(Role.MEMBER)
        tally("member:${shown.id}", shown.id, total = 300)
        tally("member:${hidden.id}", hidden.id, total = 900)
        optIn(shown, optedIn = true)
        optIn(hidden, optedIn = false)

        mvc
            .perform(get("/pinger/leaderboard"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.members.length()").value(1))
            .andExpect(jsonPath("$.members[0].memberId").value(shown.id))
            .andExpect(jsonPath("$.members[0].rank").value(1))
            .andExpect(jsonPath("$.members[0].username").value(shown.username))
            .andExpect(jsonPath("$.members[0].discordTag").doesNotExist())
    }

    @Test
    fun `opting out removes a member from the board`() {
        val member = createUserWithRole(Role.MEMBER)
        tally("member:${member.id}", member.id, total = 100)
        optIn(member, optedIn = true)
        mvc.perform(get("/pinger/leaderboard")).andExpect(jsonPath("$.members.length()").value(1))

        optIn(member, optedIn = false)

        mvc.perform(get("/pinger/leaderboard")).andExpect(jsonPath("$.members.length()").value(0))
    }

    @Test
    fun `SiteCie is a labelled house line outside the member ranking`() {
        val member = createUserWithRole(Role.MEMBER)
        tally("sitecie", null, total = 5_000)
        tally("member:${member.id}", member.id, total = 100)
        optIn(member, optedIn = true)

        mvc
            .perform(get("/pinger/leaderboard"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.house.label").value("SiteCie"))
            .andExpect(jsonPath("$.house.totalSent").value(5_000))
            .andExpect(jsonPath("$.members.length()").value(1))
            .andExpect(jsonPath("$.members[0].memberId").value(member.id))
    }

    @Test
    fun `two members swapping total swap their order`() {
        val ann = createUserWithRole(Role.MEMBER)
        val bob = createUserWithRole(Role.MEMBER)
        optIn(ann, optedIn = true)
        optIn(bob, optedIn = true)
        tally("member:${ann.id}", ann.id, total = 900)
        tally("member:${bob.id}", bob.id, total = 500)
        mvc
            .perform(get("/pinger/leaderboard"))
            .andExpect(jsonPath("$.members[0].memberId").value(ann.id))

        contributions.findAll().single { it.memberId == bob.id }.let {
            it.totalSent = 1_500
            contributions.save(it)
        }

        mvc
            .perform(get("/pinger/leaderboard"))
            .andExpect(jsonPath("$.members[0].memberId").value(bob.id))
            .andExpect(jsonPath("$.members[1].memberId").value(ann.id))
    }

    @Test
    fun `a member reads and sets their own opt-in which defaults to off`() {
        val member = createUserWithRole(Role.MEMBER)

        mvc
            .perform(get("/pinger/leaderboard/opt-in").with(signedIn(member)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.optedIn").value(false))

        mvc
            .perform(
                put("/pinger/leaderboard/opt-in")
                    .with(signedIn(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"optedIn":true}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.optedIn").value(true))

        mvc
            .perform(get("/pinger/leaderboard/opt-in").with(signedIn(member)))
            .andExpect(jsonPath("$.optedIn").value(true))
    }

    @Test
    fun `a guest may not read or set an opt-in`() {
        val guest = createUserWithRole(Role.GUEST)

        mvc
            .perform(get("/pinger/leaderboard/opt-in").with(signedIn(guest)))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `the board is readable without signing in`() {
        mvc.perform(get("/pinger/leaderboard")).andExpect(status().isOk)
    }
}
