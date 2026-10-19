package net.blueshell.api.user.web

import net.blueshell.api.auth.domain.SignupUseCases
import net.blueshell.api.auth.web.SignupMandateRequest
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.web.SignupHeaders
import net.blueshell.api.testsupport.UserTestSupport
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

    @Test
    fun `a member sets up direct debit from the bank account on file, signed today, and sees only the last two`() {
        val member = createUserWithRole(Role.MEMBER)
        createMembershipFixture(member)

        signOwnMandate(member).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("NoBankAccount"))
        putBankAccount(member).andExpect(status().isOk)
        // The mandate records the address on the account, so an account without one has nothing to record.
        signOwnMandate(member).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("MandateAddressMissing"))
        assignAddress(member)

        signOwnMandate(member, """{"authorised":false,"wordingVersion":"2026-10"}""").andExpect(status().isBadRequest)
        signOwnMandate(member, """{"authorised":true,"wordingVersion":"1999-01"}""")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("MandateWordingOutdated"))
        signOwnMandate(member)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.standing").value("MANDATE_RECORDED"))
            .andExpect(jsonPath("$.signedOn").value(LocalDate.now(ZoneOffset.UTC).toString()))
            .andExpect(jsonPath("$.ibanLastTwo").value("00"))
        mvc
            .perform(get("/users/me/mandate").with(signedIn(member)))
            .andExpect(jsonPath("$.ibanLastTwo").value("00"))
            .andExpect(jsonPath("$.reference").value("BLUESHELL-${member.id}-${LocalDate.now(ZoneOffset.UTC).toString().replace("-", "")}"))
    }

    @Test
    fun `setting up direct debit asks a step-up first`() {
        val member = createUserWithRole(Role.MEMBER)
        createMembershipFixture(member)
        assignAddress(member)
        putBankAccount(member).andExpect(status().isOk)

        signOwnMandate(member, steppedUp = false)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("StepUpRequired"))
        signOwnMandate(member).andExpect(status().isOk)
    }

    @Test
    fun `direct debit set up before any membership stands on the person, and is there once a membership starts`() {
        val applicant = createUserWithRole(Role.GUEST)
        val board = createUserWithRole(Role.BOARD)

        onlineMandateFor(applicant)
        val kept =
            jdbc.queryForMap(
                "SELECT mandate_ended_on, mandate_address, mandate_kind, mandate_wording_version, mandate_authorised_by " +
                    "FROM payment_details WHERE user_id = ?",
                applicant.id,
            )
        assertThat(kept["mandate_address"].toString()).doesNotContain("Street").doesNotContain("Enschede").doesNotContain("1234AB")
        assertThat(kept["mandate_kind"]).isEqualTo("ONLINE")
        assertThat(kept["mandate_wording_version"]).isEqualTo("2026-10")
        assertThat((kept["mandate_authorised_by"] as Number).toLong()).isEqualTo(applicant.id)
        assertThat(kept["mandate_ended_on"]).isNull()

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
        onlineMandateFor(member)
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
        signOwnMandate(member).andExpect(status().isOk)
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

        // A second step on the token with another IBAN changes the account on file, which the account's log keeps.
        mvc
            .perform(
                put("/signup/mandate")
                    .header(SignupHeaders.SIGNUP_TOKEN, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body.replace("NL91ABNA0417164300", "NL20INGB0001234567")),
            ).andExpect(status().isNoContent)
        mvc
            .perform(get("/users/${applicant.id}/security-events").with(signedIn(createUserWithRole(Role.ADMIN))))
            .andExpect(jsonPath("$.events[0].kind").value("BANK_DETAILS_CHANGED"))
    }

    @Test
    fun `the signup's incasso step needs the authorisation, and its request never shows the account number`() {
        val request = SignupMandateRequest(iban = "NL91ABNA0417164300", accountHolder = "Ann Vos")

        assertThat(request.authorised).isFalse()
        assertThat(request.toString()).contains("4300").doesNotContain("0417")
    }
}
