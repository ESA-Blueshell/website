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
    fun `a member sets up incasso on their own membership, signed today, and sees only the last four`() {
        val member = createUserWithRole(Role.MEMBER)
        createMembershipFixture(member)

        setUp(member)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.standing").value("MANDATE_RECORDED"))
            .andExpect(jsonPath("$.signedOn").value(LocalDate.now(ZoneOffset.UTC).toString()))
            .andExpect(jsonPath("$.ibanLastTwo").value("00"))
            .andExpect(jsonPath("$.pending").value(false))
        mvc
            .perform(get("/users/me/mandate").with(signedIn(member)))
            .andExpect(jsonPath("$.ibanLastTwo").value("00"))
        setUp(member, body.replace("true", "false")).andExpect(status().isBadRequest)
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
    fun `details given before the membership starts wait, and move onto it when it does`() {
        val applicant = createUserWithRole(Role.GUEST)

        setUp(applicant)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pending").value(true))
        val waiting = jdbc.queryForMap("SELECT address, wording_version FROM pending_mandates WHERE user_id = ?", applicant.id)
        assertThat(waiting["address"].toString()).doesNotContain("Hallenweg").doesNotContain("Enschede").doesNotContain("7522")
        assertThat(waiting["wording_version"]).isEqualTo("2026-10")

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
            .andExpect(jsonPath("$.ibanLastTwo").value("00"))
            .andExpect(jsonPath("$.kind").value("ONLINE"))
            .andExpect(jsonPath("$.authorisedAt").isNotEmpty)
        mvc
            .perform(get("/users/me/mandate").with(signedIn(applicant)))
            .andExpect(jsonPath("$.pending").value(false))

        // The address moved onto the membership as it was sealed, with what the authorisation recorded.
        val moved =
            jdbc.queryForMap(
                "SELECT mandate_address, mandate_kind, mandate_wording_version, mandate_authorised_by FROM memberships WHERE id = ?",
                membershipId,
            )
        assertThat(moved["mandate_address"]).isEqualTo(waiting["address"])
        assertThat(moved["mandate_kind"]).isEqualTo("ONLINE")
        assertThat(moved["mandate_wording_version"]).isEqualTo("2026-10")
        assertThat((moved["mandate_authorised_by"] as Number).toLong()).isEqualTo(applicant.id)
    }

    @Test
    fun `a paper mandate replaces an online one only once the board confirms what is lost`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val membership = createMembershipFixture(member)
        setUp(member).andExpect(status().isOk)
        val paper = """{"iban":"GB82WEST12345698765432","accountHolder":"Ann Vos","signedOn":"${LocalDate.now()}"}"""
        val record = { content: String ->
            mvc.perform(
                put("/memberships/${membership.id}/mandate").with(signedIn(board)).contentType(MediaType.APPLICATION_JSON).content(content),
            )
        }

        record(paper)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("ReplacesOnlineMandate"))
            .andExpect(jsonPath("$.authorisedAt").isNotEmpty)
        record(paper.replace("}", ""","replacesOnline":true}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.kind").value("PAPER"))
        assertThat(jdbc.queryForMap("SELECT mandate_address FROM memberships WHERE id = ?", membership.id)["mandate_address"]).isNull()

        // Online is dominant: the member authorising again replaces the paper mandate without asking.
        setUp(member).andExpect(status().isOk)
        mvc.perform(get("/memberships/${membership.id}/mandate").with(signedIn(board))).andExpect(jsonPath("$.kind").value("ONLINE"))
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
            .andExpect(jsonPath("$.pending").value(true))
    }

    @Test
    fun `the signup's incasso step needs the authorisation, and its request never shows the account number`() {
        val request = SignupMandateRequest(iban = "NL91ABNA0417164300", accountHolder = "Ann Vos")

        assertThat(request.authorised).isFalse()
        assertThat(request.toString()).contains("4300").doesNotContain("0417")
    }
}
