package net.blueshell.api.domain.contribution.web

import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@SpringBootTest
class ContributionReminderBulkControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var contributionService: ContributionService

    private fun body(
        userIds: List<Long>,
        periodId: Long,
        cutoffDate: LocalDate,
        paymentDueDate: LocalDate,
        includedUserIds: Set<Long> = emptySet(),
        feeTypeOverrides: Map<Long, BulkFeeType> = emptyMap()
    ): String {
        val includedJson = if (includedUserIds.isEmpty()) "[]" else includedUserIds.joinToString(",", "[", "]")
        val overridesJson = if (feeTypeOverrides.isEmpty()) "{}" else {
            feeTypeOverrides.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":\"$v\"" }
        }
        return """{
            "userIds":[${userIds.joinToString(",")}],
            "contributionPeriodId":$periodId,
            "cutoffDate":"$cutoffDate",
            "paymentDueDate":"$paymentDueDate",
            "includedUserIds":$includedJson,
            "feeTypeOverrides":$overridesJson
        }"""
    }

    private fun previewBody(
        userId: Long,
        periodId: Long,
        feeType: BulkFeeType,
        paymentDueDate: LocalDate,
    ): String = """{
        "userId":$userId,
        "contributionPeriodId":$periodId,
        "feeType":"$feeType",
        "paymentDueDate":"$paymentDueDate"
    }"""

    private fun markPaid(user: User, period: ContributionPeriod) = persist(
        Contribution(id = Contribution.Id(user.id, period.id), user = user, contributionPeriod = period)
    )

    private fun createMembership(
        user: User,
        memberType: MemberType,
        startDate: LocalDate = LocalDate.of(2024, 1, 1)
    ): Membership = persist(
        Membership(
            user = user,
            memberType = memberType,
            startDate = startDate,
            endDate = null,
            incasso = false
        )
    )

    @Nested
    inner class Execute {

        @Test
        fun `sends reminders to included members and writes audit rows`() {
            val board = createUserWithRole(Role.BOARD)
            val regular = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(regular, MemberType.REGULAR, LocalDate.of(2024, 1, 1))

            val cutoffDate = LocalDate.now()
            val paymentDueDate = LocalDate.now().plusDays(30)

            mvc.perform(
                post("/contributionReminders/bulk/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(regular.id!!), period.id!!, cutoffDate, paymentDueDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(1))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.queued").value(1))

            // Verify audit record was written
            transactionTemplate.execute {
                entityManager.clear()
                val reminder = entityManager.find(
                    ContributionReminder::class.java,
                    ContributionReminder.Id(regular.id, period.id)
                )
                assertThat(reminder).isNotNull
                assertThat(reminder.amount).isEqualTo(period.fullYearFee)
                assertThat(reminder.paymentDueDate).isEqualTo(paymentDueDate)
            }
        }

        @Test
        fun `honors fee type overrides in audit and email`() {
            val board = createUserWithRole(Role.BOARD)
            // Member started before cutoff → default would be FULL_YEAR_FEE; override to HALF_YEAR_FEE
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(member, MemberType.REGULAR, LocalDate.of(2024, 1, 1))

            val cutoffDate = LocalDate.now()
            val paymentDueDate = LocalDate.now().plusDays(30)

            mvc.perform(
                post("/contributionReminders/bulk/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        body(
                            listOf(member.id!!),
                            period.id!!,
                            cutoffDate,
                            paymentDueDate,
                            // The member is INCLUDED, so a real client sends them in includedUserIds
                            // (FE set = INCLUDED ∪ re-included WARNING); overrides require membership there.
                            includedUserIds = setOf(member.id!!),
                            feeTypeOverrides = mapOf(member.id!! to BulkFeeType.HALF_YEAR_FEE)
                        )
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(1))
                .andExpect(jsonPath("$.queued").value(1))

            // Verify audit record has the half-year fee amount (from the override)
            transactionTemplate.execute {
                entityManager.clear()
                val reminder = entityManager.find(
                    ContributionReminder::class.java,
                    ContributionReminder.Id(member.id, period.id)
                )
                assertThat(reminder).isNotNull
                assertThat(reminder.amount).isEqualTo(period.halfYearFee)
            }
        }

        @Test
        fun `excludes honorary members and never sends them`() {
            val board = createUserWithRole(Role.BOARD)
            val honorary = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(honorary, MemberType.HONORARY)

            val cutoffDate = LocalDate.now()
            val paymentDueDate = LocalDate.now().plusDays(30)

            mvc.perform(
                post("/contributionReminders/bulk/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(honorary.id!!), period.id!!, cutoffDate, paymentDueDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(0))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.queued").value(0))
        }

        @Test
        fun `already-paid excluded by default but can be re-included`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(member, MemberType.REGULAR)
            markPaid(member, period)

            val cutoffDate = LocalDate.now()
            val paymentDueDate = LocalDate.now().plusDays(30)

            // Execute without re-including: should skip
            mvc.perform(
                post("/contributionReminders/bulk/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!), period.id!!, cutoffDate, paymentDueDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(0))
                .andExpect(jsonPath("$.skipped").value(1))

            // Execute with re-including: should send
            mvc.perform(
                post("/contributionReminders/bulk/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        body(
                            listOf(member.id!!),
                            period.id!!,
                            cutoffDate,
                            paymentDueDate,
                            includedUserIds = setOf(member.id!!)
                        )
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(1))
                .andExpect(jsonPath("$.skipped").value(0))
        }

        @Test
        fun `skips members without email`() {
            val board = createUserWithRole(Role.BOARD)
            val noEmail = createUserWithRole(Role.MEMBER)
            noEmail.email = "" // Clear email
            persist(noEmail)

            val period = createContributionPeriodFixture()
            createMembership(noEmail, MemberType.REGULAR)

            val cutoffDate = LocalDate.now()
            val paymentDueDate = LocalDate.now().plusDays(30)

            mvc.perform(
                post("/contributionReminders/bulk/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(noEmail.id!!), period.id!!, cutoffDate, paymentDueDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(0))
                .andExpect(jsonPath("$.skipped").value(1))
        }
    }

    @Nested
    inner class MixedCohort {

        // The preview is now computed entirely client-side (bulkCompute.ts), so there is no
        // server preview endpoint to compare against. This test still pins execute's decision
        // logic for the exact mixed cohort the FE preview classifies as 2 includable
        // (regular + alumni) and 1 excluded (honorary): execute must apply 2 and skip 1.
        // See docs/proposals/bulk-actions/REDESIGN.md §7.
        @Test
        fun `execute applies includable members and skips the excluded honorary`() {
            val board = createUserWithRole(Role.BOARD)
            val regular = createUserWithRole(Role.MEMBER)
            val alumni = createUserWithRole(Role.MEMBER)
            val honorary = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(regular, MemberType.REGULAR, LocalDate.of(2024, 1, 1))
            createMembership(alumni, MemberType.ALUMNI)
            createMembership(honorary, MemberType.HONORARY)

            val userIds = listOf(regular.id!!, alumni.id!!, honorary.id!!)
            val cutoffDate = LocalDate.now()
            val paymentDueDate = LocalDate.now().plusDays(30)

            // Execute: the regular + alumni are applied, the excluded honorary is skipped.
            mvc.perform(
                post("/contributionReminders/bulk/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(userIds, period.id!!, cutoffDate, paymentDueDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(2))
                .andExpect(jsonPath("$.skipped").value(1))
        }

        @Test
        fun `execute rejects a fee override for an excluded honorary user`() {
            val board = createUserWithRole(Role.BOARD)
            val honorary = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(honorary, MemberType.HONORARY)

            mvc.perform(
                post("/contributionReminders/bulk/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        body(
                            listOf(honorary.id!!),
                            period.id!!,
                            LocalDate.of(2024, 7, 1),
                            LocalDate.now().plusDays(30),
                            includedUserIds = setOf(honorary.id!!),
                            feeTypeOverrides = mapOf(honorary.id!! to BulkFeeType.FULL_YEAR_FEE),
                        )
                    )
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class Preview {

        @Test
        fun `returns non-empty subject and html and renders neither a reminder nor a job`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(member, MemberType.REGULAR, LocalDate.of(2024, 1, 1))

            val paymentDueDate = LocalDate.now().plusDays(30)

            mvc.perform(
                post("/contributionReminders/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(previewBody(member.id!!, period.id!!, BulkFeeType.FULL_YEAR_FEE, paymentDueDate))
            )
                .andExpect(status().isOk)
                .andExpect(
                    jsonPath("$.subject").value(
                        org.hamcrest.Matchers.containsString("Please pay your Blueshell contribution")
                    )
                )
                .andExpect(jsonPath("$.subject").isNotEmpty)
                .andExpect(jsonPath("$.html").isNotEmpty)
                // Preview is self-contained: template images (logo/watermark) are inlined
                // as data URIs so the iframe shows them regardless of hosting.
                .andExpect(
                    jsonPath("$.html").value(
                        org.hamcrest.Matchers.containsString("data:image/png;base64,")
                    )
                )

            // Preview must not persist a reminder …
            transactionTemplate.execute {
                entityManager.clear()
                val reminder = entityManager.find(
                    ContributionReminder::class.java,
                    ContributionReminder.Id(member.id, period.id)
                )
                assertThat(reminder).isNull()
            }
            // … nor enqueue a send.
            assertThat(findJobsByType(EmailJobs.ContributionReminder.type)).isEmpty()
        }

        @Test
        fun `honors the requested fee type and payment due date in the rendered html`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(member, MemberType.REGULAR, LocalDate.of(2024, 1, 1))

            val paymentDueDate = LocalDate.now().plusDays(30)
            val formatted = paymentDueDate.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))

            mvc.perform(
                post("/contributionReminders/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(previewBody(member.id!!, period.id!!, BulkFeeType.HALF_YEAR_FEE, paymentDueDate))
            )
                .andExpect(status().isOk)
                .andExpect(
                    jsonPath("$.html").value(org.hamcrest.Matchers.containsString("%.2f".format(period.halfYearFee)))
                )
                .andExpect(jsonPath("$.html").value(org.hamcrest.Matchers.containsString(formatted)))
                .andExpect(
                    jsonPath("$.html").value(
                        org.hamcrest.Matchers.containsString(
                            "the half-year fee, as your membership started during the second half of the year"
                        )
                    )
                )
        }
    }

    @Nested
    inner class Authorization {

        @Test
        fun `non-board is forbidden`() {
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()

            mvc.perform(
                post("/contributionReminders/bulk/execute")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!), period.id!!, LocalDate.now(), LocalDate.now().plusDays(30)))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `non-board is forbidden from preview`() {
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()

            mvc.perform(
                post("/contributionReminders/preview")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(previewBody(member.id!!, period.id!!, BulkFeeType.FULL_YEAR_FEE, LocalDate.now().plusDays(30)))
            )
                .andExpect(status().isForbidden)
        }
    }
}
