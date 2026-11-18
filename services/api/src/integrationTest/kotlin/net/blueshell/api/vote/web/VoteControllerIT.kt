package net.blueshell.api.vote.web

import com.jayway.jsonpath.JsonPath
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import net.blueshell.api.vote.domain.TrackPreview
import net.blueshell.api.vote.domain.TrackPreviews
import net.blueshell.api.vote.domain.VoteUseCases
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

/**
 * An annual vote against MariaDB, from the board starting it through members' ballots to the
 * reveal: the changeset, the queries, the unique keys and the cascades as they really run. YouTube
 * and Spotify are stood in for; the game catalogue is the offline one the test profile uses.
 */
@SpringBootTest
class VoteControllerIT : UserTestSupport() {
    @MockitoBean
    private lateinit var previews: TrackPreviews

    @Autowired
    private lateinit var votes: VoteUseCases

    private val opens = Instant.parse("2026-12-01T00:00:00Z")
    private val closes = Instant.parse("2026-12-21T22:59:00Z")
    private val reveal = Instant.parse("2026-12-28T19:00:00Z")

    @BeforeEach
    fun duringVoting() {
        clock.set(Instant.parse("2026-12-10T12:00:00Z"))
        whenever(previews.of(any())).thenAnswer { TrackPreview("Track ${System.nanoTime()}", null) }
    }

    private fun json(result: ResultActions) = result.andReturn().response.contentAsString

    private fun startVote(board: User) =
        mvc
            .perform(
                post("/annual-votes/OST_TOP_50")
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"year":2026,"opensAt":"$opens","closesAt":"$closes","revealAt":"$reveal"}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.state").value("VOTING"))
            .andExpect(jsonPath("$.opensAt").value("2026-12-01T00:00:00Z"))

    private fun track(
        member: User,
        igdbId: Long,
        url: String,
    ): Pair<Int, Int> {
        val added =
            json(
                mvc
                    .perform(
                        post("/vote-tracks")
                            .with(signedIn(member))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"igdbId":$igdbId,"url":"$url","kind":"OST_TOP_50","year":2026}"""),
                    ).andExpect(status().isCreated),
            )
        return JsonPath.read<Int>(added, "$.candidate.id") to JsonPath.read(added, "$.id")
    }

    private fun cast(
        member: User,
        picks: List<Pair<Int, Int>>,
        version: Long?,
    ) = mvc.perform(
        put("/annual-votes/OST_TOP_50/2026/ballot")
            .with(signedIn(member))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """{"picks":[${picks.joinToString { (game, track) ->
                    """{"candidateId":$game,"trackId":$track}"""
                }}],"version":$version}""",
            ),
    )

    @Test
    fun `a member casts a ballot, reads it back, changes it at its version and is refused at a stale one`() {
        startVote(createUserWithRole(Role.BOARD))
        val member = createUserWithRole(Role.MEMBER)
        val celeste = track(member, 26226, "https://youtu.be/celeste0001")
        val witcher = track(member, 1942, "https://youtu.be/witcher0001")

        val first = json(cast(member, listOf(celeste), null).andExpect(status().isOk))
        val version = JsonPath.read<Int>(first, "$.version").toLong()
        mvc
            .perform(get("/annual-votes/OST_TOP_50/2026/ballot").with(signedIn(member)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.picks[*].candidate.name", contains("Celeste")))

        cast(member, listOf(witcher, celeste), version)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.picks[*].candidate.name", contains("The Witcher 3: Wild Hunt", "Celeste")))
        cast(member, listOf(celeste), version).andExpect(status().isConflict)
    }

    @Test
    fun `the same link is one track within a game, and its own track under another game`() {
        startVote(createUserWithRole(Role.BOARD))
        val member = createUserWithRole(Role.MEMBER)

        val first = track(member, 26226, "https://youtu.be/sharedlink1")
        val again = track(createUserWithRole(Role.MEMBER), 26226, "https://www.youtube.com/watch?v=sharedlink1")
        val elsewhere = track(member, 1942, "https://youtu.be/sharedlink1")

        assertThat(again).isEqualTo(first)
        assertThat(elsewhere.second).isNotEqualTo(first.second)
        mvc
            .perform(get("/vote-candidates/26226/tracks").with(signedIn(member)))
            .andExpect(jsonPath("$", hasSize<Any>(1)))
    }

    @Test
    fun `a merged track counts as the one it was merged into, and two merges cannot chain`() {
        val board = createUserWithRole(Role.BOARD)
        startVote(board)
        val member = createUserWithRole(Role.MEMBER)
        val kept = track(member, 26226, "https://youtu.be/keptkept001")
        val duplicate = track(member, 26226, "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC")
        val third = track(member, 26226, "https://youtu.be/thirdtrack1")
        cast(member, listOf(duplicate), null).andExpect(status().isOk)

        mvc
            .perform(
                post("/vote-tracks/{id}/merge", duplicate.second)
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"intoId":${kept.second}}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(kept.second))
        mvc
            .perform(
                post("/vote-tracks/{id}/merge", kept.second)
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"intoId":${duplicate.second}}"""),
            ).andExpect(status().isBadRequest)

        mvc
            .perform(get("/annual-votes/OST_TOP_50/2026/standings").with(signedIn(board)))
            .andExpect(jsonPath("$[0].track.id").value(kept.second))
        assertThat(third.second).isNotIn(kept.second, duplicate.second)
    }

    @Test
    fun `the reveal stores the top, deletes every ballot and pick, and only then shows the results`() {
        startVote(createUserWithRole(Role.BOARD))
        val alice = createUserWithRole(Role.MEMBER)
        val bob = createUserWithRole(Role.MEMBER)
        val celeste = track(alice, 26226, "https://youtu.be/celesterev1")
        val witcher = track(bob, 1942, "https://youtu.be/witcherrev1")
        cast(alice, listOf(celeste, witcher), null).andExpect(status().isOk)
        cast(bob, listOf(celeste), null).andExpect(status().isOk)

        mvc
            .perform(get("/annual-votes/OST_TOP_50/current"))
            .andExpect(jsonPath("$.ballots").value(2))
            .andExpect(jsonPath("$.results", hasSize<Any>(0)))

        clock.set(reveal.plusSeconds(60))
        assertThat(votes.revealDue()).isEqualTo(1)
        assertThat(votes.revealDue()).isZero()

        assertThat(rows("vote_ballots")).isZero()
        assertThat(rows("vote_ballot_picks")).isZero()
        assertThat(rows("vote_placings")).isEqualTo(2)
        mvc
            .perform(get("/annual-votes/OST_TOP_50/current"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("REVEALED"))
            .andExpect(jsonPath("$.ballots").value(2))
            .andExpect(jsonPath("$.results[*].candidate.name", contains("Celeste", "The Witcher 3: Wild Hunt")))
            .andExpect(jsonPath("$.results[0].points").value(24))
    }

    @Test
    fun `the board's dates are saved at the version it read, and refused at a stale one`() {
        val board = createUserWithRole(Role.BOARD)
        val version = JsonPath.read<Int>(json(startVote(board)), "$.version")
        val body = { seen: Int -> """{"opensAt":"$opens","closesAt":"$closes","revealAt":"${reveal.plusSeconds(3600)}","version":$seen}""" }

        mvc
            .perform(
                put("/annual-votes/OST_TOP_50/2026").with(signedIn(board)).contentType(MediaType.APPLICATION_JSON).content(body(version)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.revealAt").value("2026-12-28T20:00:00Z"))
        mvc
            .perform(
                put("/annual-votes/OST_TOP_50/2026").with(signedIn(board)).contentType(MediaType.APPLICATION_JSON).content(body(version)),
            ).andExpect(status().isConflict)
        mvc
            .perform(
                post("/annual-votes/OST_TOP_50")
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"year":2026,"opensAt":"$opens","closesAt":"$closes","revealAt":"$reveal"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("AnnualVoteExists"))
    }

    private fun rows(table: String): Long =
        transactionTemplate.execute { (entityManager.createNativeQuery("select count(*) from $table").singleResult as Number).toLong() }
}
