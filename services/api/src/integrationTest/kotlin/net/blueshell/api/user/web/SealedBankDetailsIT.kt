package net.blueshell.api.user.web

import net.blueshell.api.auth.domain.RewrapSealedValuesJob
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.api.UserJobs
import net.blueshell.api.user.domain.sealing.LocalSealer
import net.blueshell.api.user.domain.sealing.SealingUnavailable
import net.blueshell.api.user.domain.sealing.keyVersionOf
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

/** A mandate's bank details are sealed to their member: no plaintext at rest, and none that opens for somebody else. */
class SealedBankDetailsIT : UserTestSupport() {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @MockitoSpyBean
    private lateinit var sealer: LocalSealer

    @Autowired
    private lateinit var rewrap: RewrapSealedValuesJob

    private val iban = "NL91ABNA0417164300"

    private fun record(
        board: User,
        userId: Long?,
        holder: String,
    ) = mvc.perform(
        put("/users/$userId/mandate")
            .with(signedIn(board))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"iban":"$iban","accountHolder":"$holder","signedOn":"${LocalDate.now().minusDays(3)}"}"""),
    )

    private fun setUpOwn(by: User) =
        mvc.perform(
            put("/users/me/mandate")
                .with(signedIn(by, steppedUp = true))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"iban":"$iban","accountHolder":"Ann Vos","authorised":true,"wordingVersion":"2026-10",""" +
                        """"address":{"country":"NL","city":"Enschede","street":"Hallenweg","houseNumber":"5","zipCode":"7522NH"}}""",
                ),
        )

    /** A member on incasso with a recorded mandate, and their id. */
    private fun onIncasso(
        board: User,
        holder: String,
    ): Pair<User, Long> {
        val member = createUserWithRole(Role.MEMBER)
        createMembershipFixture(member, startDate = LocalDate.now().minusMonths(2))
        record(board, member.id, holder).andExpect(status().isOk)
        return member to member.id!!
    }

    private fun sealedOf(userId: Long) =
        jdbc.queryForMap("SELECT mandate_iban, mandate_account_holder FROM payment_details WHERE user_id = ?", userId)

    @Test
    fun `a paper and an online mandate hold no plaintext, and their two values share a key version`() {
        val (_, memberId) = onIncasso(createUserWithRole(Role.BOARD), "Ann Vos")
        val applicant = createUserWithRole(Role.GUEST)
        setUpOwn(applicant).andExpect(status().isOk)

        val rows = listOf(sealedOf(memberId), sealedOf(applicant.id!!))

        for (row in rows) {
            val (sealedIban, sealedHolder) = row.values.map { it.toString() }
            assertThat(sealedIban).doesNotContain("0417").doesNotContain("ABNA")
            assertThat(sealedHolder).doesNotContain("Ann").doesNotContain("Vos")
            assertThat(keyVersionOf(sealedIban)).isEqualTo(keyVersionOf(sealedHolder))
        }
    }

    @Test
    fun `a sealed account copied onto another member's mandate does not open`() {
        val board = createUserWithRole(Role.BOARD)
        val (_, annId) = onIncasso(board, "Ann Vos")
        val (_, bobId) = onIncasso(board, "Bob Smit")

        jdbc.update(
            "UPDATE payment_details theirs JOIN payment_details anns ON anns.user_id = ? " +
                "SET theirs.mandate_iban = anns.mandate_iban, theirs.mandate_account_holder = anns.mandate_account_holder " +
                "WHERE theirs.user_id = ?",
            annId,
            bobId,
        )

        mvc
            .perform(post("/users/$bobId/mandate/reveal").with(signedIn(board)))
            .andExpect(status().isConflict)
        mvc
            .perform(get("/users/$bobId/mandate").with(signedIn(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accountHolder").doesNotExist())
    }

    @Test
    fun `with the key out of reach, recording, setting up and revealing are refused, and the panel shows the masked IBAN alone`() {
        val board = createUserWithRole(Role.BOARD)
        val (_, annId) = onIncasso(board, "Ann Vos")
        val unrecorded = createMembershipFixture(createUserWithRole(Role.MEMBER))
        val applicant = createUserWithRole(Role.GUEST)
        doThrow(SealingUnavailable()).whenever(sealer).seal(any(), any())
        doThrow(SealingUnavailable()).whenever(sealer).open(any(), any())

        record(
            board,
            unrecorded.userId,
            "Cas Bos",
        ).andExpect(status().isServiceUnavailable).andExpect(jsonPath("$.code").value("SealingUnavailable"))
        setUpOwn(applicant).andExpect(status().isServiceUnavailable).andExpect(jsonPath("$.code").value("SealingUnavailable"))
        mvc
            .perform(post("/users/$annId/mandate/reveal").with(signedIn(board)))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.code").value("SealingUnavailable"))

        assertThat(sealedOf(unrecorded.userId).values).containsOnlyNulls()
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_details WHERE user_id = ?", Int::class.java, applicant.id)).isZero()
        mvc
            .perform(get("/users/$annId/mandate").with(signedIn(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ibanLastTwo").value("00"))
            .andExpect(jsonPath("$.accountHolder").doesNotExist())
    }

    @Test
    fun `the nightly rewrap moves the bank fields onto a rotated key, and they still open`() {
        val board = createUserWithRole(Role.BOARD)
        val (_, memberId) = onIncasso(board, "Ann Vos")
        val applicant = createUserWithRole(Role.GUEST)
        setUpOwn(applicant).andExpect(status().isOk)
        val newest = sealer.rotate("api-bank-details")

        rewrap.handle(mapper.writeValueAsString(UserJobs.RewrapSealedValuesPayload()), null, false)

        val online =
            jdbc.queryForMap(
                "SELECT mandate_iban, mandate_account_holder, mandate_address FROM payment_details WHERE user_id = ?",
                applicant.id,
            )
        assertThat((sealedOf(memberId).values + online.values).map { keyVersionOf(it.toString()) }).hasSize(5).containsOnly(newest)
        mvc
            .perform(get("/users/$memberId/mandate").with(signedIn(board)))
            .andExpect(jsonPath("$.accountHolder").value("Ann Vos"))
    }

    @Test
    fun `a board member reveals a full IBAN and nobody below board can, and each reveal is logged without it`() {
        val board = createUserWithRole(Role.BOARD)
        val admin = createUserWithRole(Role.ADMIN)
        val (ann, annId) = onIncasso(board, "Ann Vos")

        mvc
            .perform(post("/users/$annId/mandate/reveal").with(signedIn(board)))
            .andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.iban").value(iban))
        mvc.perform(post("/users/$annId/mandate/reveal").with(signedIn(ann))).andExpect(status().isForbidden)

        val annsLog =
            mvc
                .perform(get("/users/${ann.id}/security-events").with(signedIn(admin)))
                .andExpect(jsonPath("$.events[0].kind").value("IBAN_REVEALED"))
                .andExpect(jsonPath("$.events[0].actorName").value(board.fullName))
                .andExpect(jsonPath("$.events[0].note").value(org.hamcrest.Matchers.startsWith("mandate BLUESHELL-$annId-")))
                .andExpect(jsonPath("$.events.length()").value(1))
                .andReturn()
                .response.contentAsString
        assertThat(annsLog).doesNotContain(iban).doesNotContain("0417")
    }
}
