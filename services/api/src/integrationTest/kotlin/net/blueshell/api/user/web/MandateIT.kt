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
                "select mandate_iban, mandate_account_holder, mandate_iban_masked from payment_details where user_id = ?",
                member.id,
            )
        assertThat(stored["mandate_iban"].toString()).doesNotContain("0417").doesNotContain(iban)
        assertThat(stored["mandate_account_holder"].toString()).doesNotContain("Ann")
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
    fun `the board sets how somebody pays, without a membership, and a member cannot`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val paysBy = { by: User, incasso: Boolean ->
            mvc.perform(
                put("/users/${member.id}/pays-by")
                    .with(signedIn(by))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"incasso":$incasso}"""),
            )
        }

        paysBy(board, true)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.incasso").value(true))
            .andExpect(jsonPath("$.standing").value("ON_INCASSO_WITHOUT_BANK_DETAILS"))
        paysBy(member, false).andExpect(status().isForbidden)
        assertThat(jdbcTemplate.queryForObject("select incasso from payment_details where user_id = ?", Boolean::class.java, member.id))
            .isTrue()
    }
}
