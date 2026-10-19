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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class BankAccountIT : UserTestSupport() {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private fun replace(
        by: User,
        content: String = """{"iban":"NL91 ABNA 0417 1643 00","accountHolder":"Ann Vos"}""",
        steppedUp: Boolean = true,
    ) = mvc.perform(
        put("/users/me/bank-account")
            .with(signedIn(by, steppedUp = steppedUp))
            .contentType(MediaType.APPLICATION_JSON)
            .content(content),
    )

    @Test
    fun `a member puts their one bank account on file behind a step-up, and only its preview comes back`() {
        val member = createUserWithRole(Role.MEMBER)

        mvc.perform(get("/users/me/bank-account").with(signedIn(member))).andExpect(status().isNoContent)
        replace(member, steppedUp = false).andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("StepUpRequired"))
        val answer =
            replace(member)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.ibanCountry").value("NL"))
                .andExpect(jsonPath("$.ibanLastTwo").value("00"))
                .andReturn()
                .response.contentAsString
        assertThat(answer).doesNotContain("0417164300").doesNotContain("Ann Vos")
        mvc
            .perform(get("/users/me/bank-account").with(signedIn(member)))
            .andExpect(jsonPath("$.ibanLastTwo").value("00"))
            .andExpect(jsonPath("$.bic").doesNotExist())
            .andExpect(jsonPath("$.recordedAt").isNotEmpty)
        val sealed = jdbc.queryForObject("SELECT account_iban FROM payment_details WHERE user_id = ?", String::class.java, member.id)
        assertThat(sealed).doesNotContain("0417164300")

        replace(member, """{"iban":"DE89370400440532013000","accountHolder":"Ann Vos"}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("BicMissing"))
        replace(member, """{"iban":"DE89370400440532013000","accountHolder":"Ann Vos","bic":"COBADEFFXXX"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ibanCountry").value("DE"))
        assertThat(jdbc.queryForObject("SELECT account_bic FROM payment_details WHERE user_id = ?", String::class.java, member.id))
            .isEqualTo("COBADEFFXXX")
    }

    @Test
    fun `replacing or deleting the account ends a mandate given for it, and clears the mandate's own copy`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        createMembershipFixture(member)
        mvc
            .perform(
                put("/users/${member.id}/mandate")
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"iban":"NL91ABNA0417164300","accountHolder":"Ann Vos","signedOn":"2026-01-15"}"""),
            ).andExpect(status().isOk)

        replace(member, """{"iban":"GB82 WEST 1234 5698 7654 32","accountHolder":"Ann Vos","bic":"WESTGB2L"}""").andExpect(status().isOk)
        val copy =
            jdbc.queryForObject(
                "SELECT mandate_iban FROM payment_details WHERE user_id = ?",
                String::class.java,
                member.id,
            )
        assertThat(copy).isNull()

        mvc
            .perform(get("/users/me/mandate").with(signedIn(member)))
            .andExpect(jsonPath("$.standing").value("NONE"))
        mvc
            .perform(post("/users/${member.id}/mandate/reveal").with(signedIn(board)))
            .andExpect(jsonPath("$.iban").value("GB82WEST12345698765432"))

        mvc.perform(delete("/users/me/bank-account").with(signedIn(member))).andExpect(status().isForbidden)
        mvc.perform(delete("/users/me/bank-account").with(signedIn(member, steppedUp = true))).andExpect(status().isNoContent)
        mvc.perform(get("/users/me/bank-account").with(signedIn(member))).andExpect(status().isNoContent)
    }
}
