package net.blueshell.api.vote.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import net.blueshell.api.vote.domain.RemotePictures
import net.blueshell.api.vote.domain.TrackPreview
import net.blueshell.api.vote.domain.TrackPreviews
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import java.time.Instant

/**
 * Who may call each vote endpoint: anybody reads where a vote stands, members cast ballots and
 * find, store and add games and tracks, and only the board starts a vote, moves its dates, reads
 * the standings before the reveal and merges tracks. Each endpoint is called anonymously, by a
 * signed-in account that is not a member, by a member and by the board.
 */
@SpringBootTest
class VoteControllerSecurityTest : UserTestSupport() {
    @MockitoBean
    private lateinit var previews: TrackPreviews

    @MockitoBean
    private lateinit var pictures: RemotePictures

    private lateinit var board: User

    @BeforeEach
    fun openVote() {
        clock.set(Instant.parse("2026-12-10T12:00:00Z"))
        whenever(previews.of(any())).thenReturn(TrackPreview("Resurrections", null))
        whenever(pictures.fetch(any())).thenReturn(null)
        board = createUserWithRole(Role.BOARD)
        mvc
            .perform(
                post("/annual-votes/OST_TOP_50")
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"year":2026,$DATES}"""),
            )
    }

    enum class Caller { ANONYMOUS, NOT_A_MEMBER, MEMBER, BOARD }

    private fun call(
        endpoint: Endpoint,
        caller: Caller,
    ): Int {
        val built = request(endpoint.method, endpoint.path).contentType(MediaType.APPLICATION_JSON).content(endpoint.body)
        val asked: MockHttpServletRequestBuilder =
            when (caller) {
                Caller.ANONYMOUS -> built
                Caller.NOT_A_MEMBER -> built.with(signedIn(createUserWithRole(Role.GUEST)))
                Caller.MEMBER -> built.with(signedIn(createUserWithRole(Role.MEMBER)))
                Caller.BOARD -> built.with(signedIn(board))
            }
        return mvc
            .perform(asked)
            .andReturn()
            .response.status
    }

    class Endpoint(
        val method: HttpMethod,
        val path: String,
        val body: String = "",
        val who: Who,
    ) {
        override fun toString() = "$method $path"
    }

    enum class Who { ANYBODY, MEMBERS, BOARD }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    fun `each endpoint answers only those it is for`(endpoint: Endpoint) {
        val anonymous = call(endpoint, Caller.ANONYMOUS)
        val stranger = call(endpoint, Caller.NOT_A_MEMBER)
        val member = call(endpoint, Caller.MEMBER)
        val boardMember = call(endpoint, Caller.BOARD)

        when (endpoint.who) {
            Who.ANYBODY -> assertThat(listOf(anonymous, stranger, member, boardMember)).allMatch { it == 200 }
            Who.MEMBERS -> {
                assertThat(anonymous).isEqualTo(401)
                assertThat(stranger).isEqualTo(403)
                assertThat(member).isNotIn(401, 403)
            }
            Who.BOARD -> {
                assertThat(anonymous).isEqualTo(401)
                assertThat(stranger).isEqualTo(403)
                assertThat(member).isEqualTo(403)
                assertThat(boardMember).isNotIn(401, 403)
            }
        }
    }

    companion object {
        private const val DATES = """"opensAt":"2026-12-01T00:00:00Z","closesAt":"2026-12-21T22:59:00Z","revealAt":"2026-12-28T19:00:00Z""""

        @JvmStatic
        fun endpoints(): List<Arguments> =
            listOf(
                Endpoint(HttpMethod.GET, "/annual-votes/OST_TOP_50/current", who = Who.ANYBODY),
                Endpoint(HttpMethod.POST, "/annual-votes/OST_TOP_50", """{"year":2027,$DATES}""", Who.BOARD),
                Endpoint(HttpMethod.PUT, "/annual-votes/OST_TOP_50/2026", """{$DATES,"version":0}""", Who.BOARD),
                Endpoint(HttpMethod.GET, "/annual-votes/OST_TOP_50/2026/standings", who = Who.BOARD),
                Endpoint(HttpMethod.GET, "/annual-votes/OST_TOP_50/2026/ballot", who = Who.MEMBERS),
                Endpoint(
                    HttpMethod.PUT,
                    "/annual-votes/OST_TOP_50/2026/ballot",
                    """{"picks":[{"candidateId":1}],"version":null}""",
                    Who.MEMBERS,
                ),
                Endpoint(HttpMethod.GET, "/vote-candidates?query=cel&kind=OST_TOP_50&year=2026", who = Who.MEMBERS),
                Endpoint(HttpMethod.GET, "/vote-candidates/covers/co1abc", who = Who.MEMBERS),
                Endpoint(HttpMethod.POST, "/vote-candidates", """{"igdbId":26226,"kind":"OST_TOP_50","year":2026}""", Who.MEMBERS),
                Endpoint(HttpMethod.GET, "/vote-candidates/26226/tracks", who = Who.MEMBERS),
                Endpoint(
                    HttpMethod.POST,
                    "/vote-tracks",
                    """{"igdbId":26226,"url":"https://youtu.be/dQw4w9WgXcQ","kind":"OST_TOP_50","year":2026}""",
                    Who.MEMBERS,
                ),
                Endpoint(HttpMethod.POST, "/vote-tracks/1/merge", """{"intoId":2}""", Who.BOARD),
            ).map { Arguments.of(it) }
    }
}
