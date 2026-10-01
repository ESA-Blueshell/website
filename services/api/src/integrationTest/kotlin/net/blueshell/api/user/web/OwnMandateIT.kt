package net.blueshell.api.user.web

import net.blueshell.api.auth.domain.SignupUseCases
import net.blueshell.api.auth.web.SignupMandateRequest
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.web.SignupHeaders
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.ZoneOffset

@SpringBootTest
class OwnMandateIT : UserTestSupport() {
    @Autowired
    private lateinit var signups: SignupUseCases

    private val body = """{"iban":"NL91ABNA0417164300","accountHolder":"Ann Vos","authorised":true}"""

    private fun setUp(
        by: User,
        content: String = body,
    ) = mvc.perform(
        put("/users/me/mandate")
            .with(signedIn(by))
            .contentType(MediaType.APPLICATION_JSON)
            .content(content),
    )

    @Test
    fun `a member sets up incasso on their own membership, signed today, and sees only the last four`() {
        val member = createUserWithRole(Role.MEMBER)
        createMembershipFixture(member)

        setUp(member)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.standing").value("MANDATE_RECORDED"))
            .andExpect(jsonPath("$.signedOn").value(LocalDate.now(ZoneOffset.UTC).toString()))
            .andExpect(jsonPath("$.ibanLastFour").value("4300"))
            .andExpect(jsonPath("$.pending").value(false))
        mvc
            .perform(get("/users/me/mandate").with(signedIn(member)))
            .andExpect(jsonPath("$.ibanLastFour").value("4300"))
        setUp(member, body.replace("true", "false")).andExpect(status().isBadRequest)
    }

    @Test
    fun `details given before the membership starts wait, and move onto it when it does`() {
        val applicant = createUserWithRole(Role.GUEST)

        setUp(applicant)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pending").value(true))

        val created =
            mvc
                .perform(
                    post("/users/${applicant.id}/memberships")
                        .with(signedIn(createUserWithRole(Role.BOARD)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"userId":${applicant.id},"startDate":"${LocalDate.now()}","memberType":"REGULAR","incasso":false}"""),
                ).andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString
        val membershipId = Regex("\"id\":(\\d+)").find(created)!!.groupValues[1]

        mvc
            .perform(get("/memberships/$membershipId/mandate").with(signedIn(createUserWithRole(Role.BOARD))))
            .andExpect(jsonPath("$.standing").value("MANDATE_RECORDED"))
            .andExpect(jsonPath("$.ibanLastFour").value("4300"))
        mvc
            .perform(get("/users/me/mandate").with(signedIn(applicant)))
            .andExpect(jsonPath("$.pending").value(false))
    }

    @Test
    fun `the signup's incasso step holds the details on the signup token`() {
        val applicant = createUserWithRole(Role.GUEST, enabled = false)
        val token = signups.issueSession(applicant.id!!).token

        mvc
            .perform(
                put("/signup/mandate")
                    .header(SignupHeaders.SIGNUP_TOKEN, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isNoContent)
        mvc
            .perform(get("/users/me/mandate").with(signedIn(applicant)))
            .andExpect(jsonPath("$.pending").value(true))
    }

    @Test
    fun `the signup's incasso step needs the authorisation, and its request never shows the account number`() {
        val request = SignupMandateRequest(iban = "NL91ABNA0417164300", accountHolder = "Ann Vos")

        assertThat(request.authorised).isFalse()
        assertThat(request.toString()).contains("4300").doesNotContain("0417")
    }
}
