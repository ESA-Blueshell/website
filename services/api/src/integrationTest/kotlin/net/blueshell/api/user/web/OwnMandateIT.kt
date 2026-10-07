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
import org.springframework.jdbc.core.JdbcTemplate
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

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private val body =
        """{"iban":"NL91ABNA0417164300","accountHolder":"Ann Vos","authorised":true,"wordingVersion":"2026-10",""" +
            """"address":{"country":"NL","city":"Enschede","street":"Hallenweg","houseNumber":"5","zipCode":"7522NH"}}"""

    private fun setUp(
        by: User,
        content: String = body,
        steppedUp: Boolean = true,
    ) = mvc.perform(
        put("/users/me/mandate")
            .with(signedIn(by, steppedUp = steppedUp))
            .contentType(MediaType.APPLICATION_JSON)
            .content(content),
    )

    @Test
    fun `a member sets up incasso themselves, signed today, and sees only the last four`() {
        val member = createUserWithRole(Role.MEMBER)
        createMembershipFixture(member)

        setUp(member)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.standing").value("MANDATE_RECORDED"))
            .andExpect(jsonPath("$.signedOn").value(LocalDate.now(ZoneOffset.UTC).toString()))
            .andExpect(jsonPath("$.ibanLastTwo").value("00"))
        mvc
            .perform(get("/users/me/mandate").with(signedIn(member)))
            .andExpect(jsonPath("$.ibanLastTwo").value("00"))
        setUp(member, body.replace("true", "false")).andExpect(status().isBadRequest)
        // What a request may carry is bounded, so a sealed value always fits its column.
        setUp(member, body.replace("Ann Vos", "A".repeat(71))).andExpect(status().isBadRequest)
        setUp(member, body.replace("Hallenweg", "H".repeat(151))).andExpect(status().isBadRequest)
        setUp(member, body.replace("\"NL\"", "\"Nowhere\"")).andExpect(status().isBadRequest)
    }

    @Test
    fun `a change from the account page asks a step-up first, and is written to the security log`() {
        val member = createUserWithRole(Role.MEMBER)
        createMembershipFixture(member)

        setUp(member, steppedUp = false)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("StepUpRequired"))
        setUp(member).andExpect(status().isOk)

        mvc
            .perform(get("/users/me/security-events").with(signedIn(member)))
            .andExpect(jsonPath("$.events[0].kind").value("BANK_DETAILS_CHANGED"))
            .andExpect(jsonPath("$.events[0].note").value("NL•• … ••00"))
    }

    @Test
    fun `details given before any membership stand on the person, and are there once a membership starts`() {
        val applicant = createUserWithRole(Role.GUEST)
        val board = createUserWithRole(Role.BOARD)

        setUp(applicant)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.standing").value("MANDATE_RECORDED"))
        val kept =
            jdbc.queryForMap(
                "SELECT incasso, mandate_address, mandate_kind, mandate_wording_version, mandate_authorised_by FROM payment_details WHERE user_id = ?",
                applicant.id,
            )
        assertThat(kept["mandate_address"].toString()).doesNotContain("Hallenweg").doesNotContain("Enschede").doesNotContain("7522")
        assertThat(kept["mandate_kind"]).isEqualTo("ONLINE")
        assertThat(kept["mandate_wording_version"]).isEqualTo("2026-10")
        assertThat((kept["mandate_authorised_by"] as Number).toLong()).isEqualTo(applicant.id)
        assertThat(kept["incasso"]).isEqualTo(true)

        mvc
            .perform(
                post("/users/${applicant.id}/memberships")
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${applicant.id},"startDate":"${LocalDate.now()}","memberType":"REGULAR"}"""),
            ).andExpect(status().isCreated)

        mvc
            .perform(get("/users/${applicant.id}/mandate").with(signedIn(board)))
            .andExpect(jsonPath("$.standing").value("MANDATE_RECORDED"))
            .andExpect(jsonPath("$.ibanLastTwo").value("00"))
            .andExpect(jsonPath("$.kind").value("ONLINE"))
            .andExpect(jsonPath("$.authorisedAt").isNotEmpty)
        assertThat(jdbc.queryForObject("SELECT mandate_address FROM payment_details WHERE user_id = ?", String::class.java, applicant.id))
            .isEqualTo(kept["mandate_address"])
    }

    @Test
    fun `a paper mandate replaces an online one only once the board confirms what is lost`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        createMembershipFixture(member)
        setUp(member).andExpect(status().isOk)
        val paper = """{"iban":"GB82WEST12345698765432","accountHolder":"Ann Vos","signedOn":"${LocalDate.now()}"}"""
        val record = { content: String ->
            mvc.perform(
                put("/users/${member.id}/mandate").with(signedIn(board)).contentType(MediaType.APPLICATION_JSON).content(content),
            )
        }

        record(paper)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ReplacesOnlineMandate"))
            .andExpect(jsonPath("$.authorisedAt").isNotEmpty)
        record(paper.replace("}", ""","replacesOnline":true}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.kind").value("PAPER"))
        assertThat(jdbc.queryForMap("SELECT mandate_address FROM payment_details WHERE user_id = ?", member.id)["mandate_address"]).isNull()

        // Online is dominant: the member authorising again replaces the paper mandate without asking.
        setUp(member).andExpect(status().isOk)
        mvc.perform(get("/users/${member.id}/mandate").with(signedIn(board))).andExpect(jsonPath("$.kind").value("ONLINE"))
    }

    @Test
    fun `the signup's incasso step holds the details on the signup token`() {
        val applicant = createUserWithRole(Role.GUEST, enabled = false)
        val token = signups.issueSession(applicant.id!!).token

        // Without the address the signup takes first, the mandate has nothing to record.
        mvc
            .perform(put("/signup/mandate").header(SignupHeaders.SIGNUP_TOKEN, token).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("MandateAddressMissing"))
        assignAddress(applicant)

        mvc
            .perform(
                put("/signup/mandate")
                    .header(SignupHeaders.SIGNUP_TOKEN, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isNoContent)
        mvc
            .perform(get("/users/me/mandate").with(signedIn(applicant)))
            .andExpect(jsonPath("$.standing").value("MANDATE_RECORDED"))
    }

    @Test
    fun `the signup's incasso step needs the authorisation, and its request never shows the account number`() {
        val request = SignupMandateRequest(iban = "NL91ABNA0417164300", accountHolder = "Ann Vos")

        assertThat(request.authorised).isFalse()
        assertThat(request.toString()).contains("4300").doesNotContain("0417")
    }
}
