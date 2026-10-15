package net.blueshell.api.user.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.ZoneOffset

@SpringBootTest
class MandateIT : UserTestSupport() {
    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val iban = "NL91ABNA0417164300"
    private val body = """{"iban":"NL91 ABNA 0417 1643 00","accountHolder":"Ann Vos","signedOn":"2026-01-15"}"""

    private fun record(
        userId: Long?,
        by: User,
        content: String = body,
    ) = mvc.perform(
        put("/users/$userId/mandate")
            .with(signedIn(by))
            .contentType(MediaType.APPLICATION_JSON)
            .content(content),
    )

    @Test
    fun `the board records a mandate sealed at rest, and no response carries more than the last four`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        createMembershipFixture(member)

        record(member.id, board)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ibanCountry").value("NL"))
            .andExpect(jsonPath("$.ibanLastTwo").value("00"))
            .andExpect(jsonPath("$.standing").value("MANDATE_RECORDED"))
            .andExpect(jsonPath("$.recordedBy").value(board.id!!.toInt()))

        val stored =
            jdbcTemplate.queryForMap(
                "select account_iban, account_holder, mandate_iban_masked from payment_details where user_id = ?",
                member.id,
            )
        assertThat(stored["account_iban"].toString()).doesNotContain("0417").doesNotContain(iban)
        assertThat(stored["account_holder"].toString()).doesNotContain("Ann")
        assertThat(stored["mandate_iban_masked"]).isEqualTo("NL00")

        val paths =
            listOf(
                "/users/${member.id}/mandate",
                "/memberships",
                "/memberships?userId=${member.id}",
                "/users/${member.id}/memberships",
            )
        for (path in paths) {
            val answer =
                mvc
                    .perform(get(path).with(signedIn(board)))
                    .andReturn()
                    .response.contentAsString
            assertThat(answer).describedAs(path).doesNotContain("0417").doesNotContain("ABNA0417")
        }
    }

    @Test
    fun `a wrong IBAN is refused, and members cannot record or read a mandate`() {
        val member = createUserWithRole(Role.MEMBER)
        createMembershipFixture(member)

        record(member.id, createUserWithRole(Role.BOARD), body.replace("NL91", "NL92"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("InvalidIban"))
        record(member.id, member).andExpect(status().isForbidden)
        mvc.perform(get("/users/${member.id}/mandate").with(signedIn(member))).andExpect(status().isForbidden)
    }

    @Test
    fun `the board ends somebody's mandate, a member cannot, and the member stops their own`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        record(member.id, board).andExpect(status().isOk)
        val end = { by: User -> mvc.perform(post("/users/${member.id}/mandate/end").with(signedIn(by))) }

        end(member).andExpect(status().isForbidden)
        end(board)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.standing").value("NONE"))
            .andExpect(jsonPath("$.endedOn").value(LocalDate.now(ZoneOffset.UTC).toString()))

        record(member.id, board).andExpect(status().isOk)
        mvc
            .perform(post("/users/me/mandate/end").with(signedIn(member)))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("StepUpRequired"))
        mvc
            .perform(post("/users/me/mandate/end").with(signedIn(member, steppedUp = true)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.standing").value("NONE"))
    }

    @Test
    fun `a mandate ends on the day the membership ends, and rejoining starts without one`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val membership = createMembershipFixture(user = member, startDate = LocalDate.now().minusYears(1))
        record(member.id, board).andExpect(status().isOk)

        mvc.perform(post("/memberships/${membership.id}/end").with(signedIn(board))).andExpect(status().isOk)

        mvc
            .perform(get("/users/${member.id}/mandate").with(signedIn(board)))
            .andExpect(jsonPath("$.standing").value("NONE"))
            .andExpect(jsonPath("$.endedOn").value(LocalDate.now().toString()))
    }
}
