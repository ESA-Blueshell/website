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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class MandateIT : UserTestSupport() {
    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val iban = "NL91ABNA0417164300"
    private val body = """{"iban":"NL91 ABNA 0417 1643 00","accountHolder":"Ann Vos","signedOn":"2026-01-15"}"""

    private fun record(
        membershipId: Long?,
        by: User,
        content: String = body,
    ) = mvc.perform(
        put("/memberships/$membershipId/mandate")
            .with(signedIn(by))
            .contentType(MediaType.APPLICATION_JSON)
            .content(content),
    )

    @Test
    fun `the board records a mandate sealed at rest, and no response carries more than the last four`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val membership = createMembershipFixture(member)

        record(membership.id, board)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ibanLastFour").value("4300"))
            .andExpect(jsonPath("$.standing").value("MANDATE_RECORDED"))
            .andExpect(jsonPath("$.recordedBy").value(board.id!!.toInt()))

        val stored =
            jdbcTemplate.queryForMap(
                "select mandate_iban, mandate_account_holder, mandate_iban_last_four from memberships where id = ?",
                membership.id,
            )
        assertThat(stored["mandate_iban"].toString()).doesNotContain("0417").doesNotContain(iban)
        assertThat(stored["mandate_account_holder"].toString()).doesNotContain("Ann")
        assertThat(stored["mandate_iban_last_four"]).isEqualTo("4300")

        val paths =
            listOf(
                "/memberships/${membership.id}/mandate",
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
        val membership = createMembershipFixture(member)

        record(membership.id, createUserWithRole(Role.BOARD), body.replace("NL91", "NL92"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("InvalidIban"))
        record(membership.id, member).andExpect(status().isForbidden)
        mvc.perform(get("/memberships/${membership.id}/mandate").with(signedIn(member))).andExpect(status().isForbidden)
    }
}
