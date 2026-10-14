package net.blueshell.api.user.web

import com.jayway.jsonpath.JsonPath
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.MemberRepository
import net.blueshell.api.user.persistence.User
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@SpringBootTest
class MembershipTypeIT : UserTestSupport() {
    @Autowired
    private lateinit var memberships: MemberRepository

    private val board by lazy { createUserWithRole(Role.BOARD) }
    private val start = LocalDate.now().minusYears(3)
    private val alumniSince = LocalDate.now().minusMonths(6)

    private fun started(member: User = createUserWithRole(Role.GUEST)): Long =
        mvc
            .perform(
                post("/users/${member.id}/memberships")
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${member.id},"startDate":"$start","memberType":"REGULAR"}"""),
            ).andExpect(status().isCreated)
            .andReturn()
            .response.contentAsString
            .let { JsonPath.read<Number>(it, "$.id").toLong() }

    private fun record(
        membershipId: Long,
        type: String,
        since: LocalDate,
        by: User = board,
    ): ResultActions =
        mvc.perform(
            post("/memberships/$membershipId/types")
                .with(signedIn(by))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"$type","since":"$since"}"""),
        )

    private fun membershipTypeIds(membershipId: Long): List<Long> =
        transactionTemplate.execute {
            memberships
                .findById(membershipId)
                .orElseThrow()
                .types
                .map { it.id!! }
        }

    private fun versionOf(membershipId: Long): Long = memberships.findById(membershipId).orElseThrow().version

    @Test
    fun `a new membership holds the type it started as from its start`() {
        val membership = started()

        mvc
            .perform(
                post("/memberships/$membership/types")
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"type":"ALUMNI","since":"$alumniSince"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.memberType").value("ALUMNI"))
            .andExpect(jsonPath("$.endDate").doesNotExist())
            .andExpect(jsonPath("$.types.length()").value(2))
            .andExpect(jsonPath("$.types[0].type").value("REGULAR"))
            .andExpect(jsonPath("$.types[0].since").value(start.toString()))
            .andExpect(jsonPath("$.types[1].type").value("ALUMNI"))
            .andExpect(jsonPath("$.types[1].since").value(alumniSince.toString()))
    }

    @Test
    fun `a type is recorded within the membership, one a day`() {
        val membership = started()

        record(membership, "ALUMNI", start.minusDays(1))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("MembershipTypeOutsideMembership"))
        record(membership, "ALUMNI", start)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("MembershipTypeOnTakenDay"))
        record(membership, "ALUMNI", LocalDate.now().plusDays(1)).andExpect(status().isBadRequest)
    }

    @Test
    fun `the board corrects and removes a later type, never the first`() {
        val membership = started()
        record(membership, "ALUMNI", alumniSince).andExpect(status().isOk)
        val (first, alumni) = membershipTypeIds(membership)

        mvc
            .perform(
                put("/memberships/$membership/types/$alumni")
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"type":"HONORARY","since":"${alumniSince.minusMonths(1)}"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.memberType").value("HONORARY"))
            .andExpect(jsonPath("$.pending").value(false))
            .andExpect(jsonPath("$.types[1].since").value(alumniSince.minusMonths(1).toString()))

        mvc
            .perform(
                put("/memberships/$membership/types/$first")
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"type":"ALUMNI","since":"${start.plusDays(1)}"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("FirstMembershipTypeStartsTheMembership"))
        mvc
            .perform(delete("/memberships/$membership/types/$first").with(signedIn(board)))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("FirstMembershipTypeStartsTheMembership"))

        mvc
            .perform(delete("/memberships/$membership/types/$alumni").with(signedIn(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.memberType").value("REGULAR"))
            .andExpect(jsonPath("$.types.length()").value(1))
    }

    @Test
    fun `moving the start moves the first type, and the membership cannot end before a type it holds`() {
        val member = createUserWithRole(Role.GUEST)
        val membership = started(member)
        record(membership, "ALUMNI", alumniSince).andExpect(status().isOk)

        mvc
            .perform(
                put("/memberships/$membership")
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${member.id},"startDate":"${start.plusDays(10)}","version":${versionOf(membership)}}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.types[0].since").value(start.plusDays(10).toString()))

        mvc
            .perform(
                put("/memberships/$membership")
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"userId":${member.id},"startDate":"$start","endDate":"$alumniSince","version":${versionOf(membership)}}""",
                    ),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("MembershipTypeOutsideMembership"))
    }

    @Test
    fun `a member reads their own memberships with the types in them`() {
        val member = createUserWithRole(Role.MEMBER)
        val membership = started(member)
        record(membership, "ALUMNI", alumniSince).andExpect(status().isOk)
        createMembershipFixture()

        mvc
            .perform(get("/users/me/memberships").with(signedIn(member)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].memberType").value("ALUMNI"))
            .andExpect(jsonPath("$[0].types[1].since").value(alumniSince.toString()))
    }

    @Test
    fun `only the board records a type`() {
        val member = createUserWithRole(Role.MEMBER)
        val membership = started(member)

        record(membership, "ALUMNI", alumniSince, by = member).andExpect(status().isForbidden)
    }

    @Test
    fun `a new membership starts only after a gap`() {
        val member = createUserWithRole(Role.GUEST)
        val ended = LocalDate.now().minusMonths(1)
        createMembershipFixture(user = member, startDate = start, endDate = ended)

        mvc
            .perform(
                post("/users/${member.id}/memberships")
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${member.id},"startDate":"$ended","memberType":"ALUMNI"}"""),
            ).andExpect(status().isBadRequest)
    }
}
