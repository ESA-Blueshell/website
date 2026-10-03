package net.blueshell.api.contribution.domain

import net.blueshell.api.jobs.api.JobOutcome
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.api.UserErasureService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.ZoneOffset

/** Bank details outlive the last collection by 13 months once collecting stops, and are wiped then. */
class BankDetailsWipeIT : UserTestSupport() {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var job: WipeBankDetailsJob

    @Autowired
    private lateinit var erasure: UserErasureService

    private val payload get() = mapper.writeValueAsString(ContributionJobs.WipeBankDetailsPayload())
    private val online =
        """{"iban":"NL91ABNA0417164300","accountHolder":"Ann Vos","authorised":true,"wordingVersion":"2026-10",""" +
            """"address":{"country":"NL","city":"Enschede","street":"Hallenweg","houseNumber":"5","zipCode":"7522NH"}}"""

    @AfterEach
    fun resetClock() = clock.reset()

    private fun authorise(member: User) =
        mvc
            .perform(
                put("/users/me/mandate").with(signedIn(member, steppedUp = true)).contentType(MediaType.APPLICATION_JSON).content(online),
            ).andExpect(status().isOk)

    /** A member whose membership carries an online mandate, and the membership's id. */
    private fun onIncasso(): Pair<User, Long> {
        val member = createUserWithRole(Role.MEMBER)
        val membership = createMembershipFixture(member, startDate = LocalDate.now().minusMonths(2))
        authorise(member)
        return member to membership.id!!
    }

    /** A run collecting from [member] on [collectionDate], which the board submitted to ING. */
    private fun collected(
        board: User,
        member: User,
        collectionDate: LocalDate,
    ) {
        val period = createContributionPeriodFixture()
        val answer =
            mvc
                .perform(
                    post("/contributionPeriods/${period.id}/incassoRuns")
                        .with(signedIn(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"userIds":[${member.id}],"collectionDate":"$collectionDate","statementText":"Contributie"}"""),
                ).andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString
        val runId = Regex("\"id\":(\\d+)").find(answer)!!.groupValues[1]
        mvc.perform(post("/incassoRuns/$runId/submitted").with(signedIn(board))).andExpect(status().isOk)
    }

    private fun mandateOf(membershipId: Long) =
        jdbc.queryForMap(
            "SELECT mandate_iban, mandate_account_holder, mandate_address, mandate_reference, mandate_signed_on, mandate_iban_masked " +
                "FROM memberships WHERE id = ?",
            membershipId,
        )

    private fun sealedOf(membershipId: Long) =
        mandateOf(membershipId)
            .filterKeys {
                it in
                    setOf("mandate_iban", "mandate_account_holder", "mandate_address")
            }.values

    private fun runOn(day: LocalDate): JobOutcome {
        clock.set(day.atTime(4, 30).toInstant(ZoneOffset.UTC))
        return job.handle(payload, null, false)
    }

    @Test
    fun `a mandate no longer collected from is wiped 13 months after its last collection, and keeps what it was collected under`() {
        val board = createUserWithRole(Role.BOARD)
        val (member, membershipId) = onIncasso()
        val collection = LocalDate.now().plusDays(7)
        collected(board, member, collection)
        jdbc.update("UPDATE memberships SET incasso = FALSE WHERE id = ?", membershipId)

        assertThat(runOn(collection.plusMonths(13).minusDays(1))).isInstanceOf(JobOutcome.Skipped::class.java)
        assertThat(sealedOf(membershipId)).doesNotContainNull()

        assertThat(runOn(collection.plusMonths(13))).isInstanceOf(JobOutcome.Done::class.java)

        val kept = mandateOf(membershipId)
        assertThat(sealedOf(membershipId)).containsOnlyNulls()
        assertThat(kept["mandate_reference"].toString()).startsWith("BLUESHELL-$membershipId-")
        assertThat(kept["mandate_signed_on"]).isNotNull()
        assertThat(kept["mandate_iban_masked"]).isEqualTo("NL00")
        mvc
            .perform(get("/memberships/$membershipId/mandate").with(signedIn(board)))
            .andExpect(jsonPath("$.bankDetailsWiped").value(true))
            .andExpect(jsonPath("$.standing").value("NONE"))
            .andExpect(jsonPath("$.ibanLastTwo").value("00"))
        assertThat(runOn(collection.plusMonths(14))).isInstanceOf(JobOutcome.Skipped::class.java)
    }

    @Test
    fun `a mandate never collected from is wiped once collecting stops, and one still collected from is left alone`() {
        val (_, stopped) = onIncasso()
        val (_, ended) = onIncasso()
        val (_, running) = onIncasso()
        jdbc.update("UPDATE memberships SET incasso = FALSE WHERE id = ?", stopped)
        jdbc.update("UPDATE memberships SET end_date = ? WHERE id = ?", LocalDate.now(), ended)

        assertThat(runOn(LocalDate.now())).isInstanceOf(JobOutcome.Done::class.java)

        assertThat(sealedOf(stopped)).containsOnlyNulls()
        assertThat(sealedOf(ended)).containsOnlyNulls()
        assertThat(sealedOf(running)).doesNotContainNull()
        assertThat(runOn(LocalDate.now().plusYears(3))).isInstanceOf(JobOutcome.Skipped::class.java)
        assertThat(sealedOf(running)).doesNotContainNull()
    }

    @Test
    fun `erasing an account wipes its pending mandate at once and starts the 13 months for a mandate collected under`() {
        val board = createUserWithRole(Role.BOARD)
        val applicant = createUserWithRole(Role.GUEST)
        authorise(applicant)
        val (member, membershipId) = onIncasso()
        val collection = LocalDate.now().plusDays(7)
        collected(board, member, collection)

        erasure.deleteUser(applicant.id!!)
        erasure.deleteUser(member.id!!)

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pending_mandates WHERE user_id = ?", Int::class.java, applicant.id)).isZero()
        runOn(collection.plusMonths(13).minusDays(1))
        assertThat(sealedOf(membershipId)).doesNotContainNull()
        assertThat(runOn(collection.plusMonths(13))).isInstanceOf(JobOutcome.Done::class.java)
        assertThat(sealedOf(membershipId)).containsOnlyNulls()
    }
}
