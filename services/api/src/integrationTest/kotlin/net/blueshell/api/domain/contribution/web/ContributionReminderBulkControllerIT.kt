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
    inner class Preview {

        @Test
        fun `regular member starting before cutoff resolves full-year fee`() {
            val board = createUserWithRole(Role.BOARD)
            val regular = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(regular, MemberType.REGULAR, LocalDate.of(2024, 1, 1))

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val paymentDueDate = LocalDate.of(2024, 12, 31)

            mvc.perform(
                post("/contributionReminders/bulk/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(regular.id!!), period.id!!, cutoffDate, paymentDueDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.counts.selected").value(1))
                .andExpect(jsonPath("$.counts.willApply").value(1))
                .andExpect(jsonPath("$.counts.excluded").value(0))
                .andExpect(jsonPath("$.rows[0].amount").value(period.fullYearFee))
                .andExpect(jsonPath("$.rows[0].recommendedFeeType").value("FULL_YEAR_FEE"))
                .andExpect(jsonPath("$.rows[0].disposition").value("INCLUDED"))
                .andExpect(jsonPath("$.rows[0].memberType").value("REGULAR"))
        }

        @Test
        fun `regular member starting after cutoff resolves half-year fee`() {
            val board = createUserWithRole(Role.BOARD)
            val regular = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(regular, MemberType.REGULAR, LocalDate.of(2024, 8, 1))

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val paymentDueDate = LocalDate.of(2024, 12, 31)

            mvc.perform(
                post("/contributionReminders/bulk/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(regular.id!!), period.id!!, cutoffDate, paymentDueDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.rows[0].amount").value(period.halfYearFee))
                .andExpect(jsonPath("$.rows[0].recommendedFeeType").value("HALF_YEAR_FEE"))
        }

        @Test
        fun `alumni member resolves alumni fee`() {
            val board = createUserWithRole(Role.BOARD)
            val alumni = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(alumni, MemberType.ALUMNI, LocalDate.of(2023, 1, 1))

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val paymentDueDate = LocalDate.of(2024, 12, 31)

            mvc.perform(
                post("/contributionReminders/bulk/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(alumni.id!!), period.id!!, cutoffDate, paymentDueDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.rows[0].amount").value(period.alumniFee))
                .andExpect(jsonPath("$.rows[0].recommendedFeeType").value("ALUMNI_FEE"))
                .andExpect(jsonPath("$.rows[0].memberType").value("ALUMNI"))
        }

        @Test
        fun `honorary member is excluded and shown red`() {
            val board = createUserWithRole(Role.BOARD)
            val honorary = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(honorary, MemberType.HONORARY)

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val paymentDueDate = LocalDate.of(2024, 12, 31)

            mvc.perform(
                post("/contributionReminders/bulk/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(honorary.id!!), period.id!!, cutoffDate, paymentDueDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.counts.excluded").value(1))
                .andExpect(jsonPath("$.counts.willApply").value(0))
                .andExpect(jsonPath("$.rows[0].disposition").value("EXCLUDED"))
                .andExpect(jsonPath("$.rows[0].reason").value("HONORARY"))
                .andExpect(jsonPath("$.rows[0].amount").doesNotExist())
                .andExpect(jsonPath("$.rows[0].memberType").value("HONORARY"))
        }

        @Test
        fun `already-paid member is excluded by default with warning disposition`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(member, MemberType.REGULAR)
            markPaid(member, period)

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val paymentDueDate = LocalDate.of(2024, 12, 31)

            mvc.perform(
                post("/contributionReminders/bulk/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!), period.id!!, cutoffDate, paymentDueDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.counts.warned").value(1))
                .andExpect(jsonPath("$.counts.willApply").value(0))
                .andExpect(jsonPath("$.rows[0].disposition").value("WARNING"))
                .andExpect(jsonPath("$.rows[0].reason").value("ALREADY_PAID"))
                .andExpect(jsonPath("$.rows[0].amount").value(period.fullYearFee))
        }

        @Test
        fun `mixed selection resolves correctly`() {
            val board = createUserWithRole(Role.BOARD)
            val regularUnpaid = createUserWithRole(Role.MEMBER)
            val alreadyPaid = createUserWithRole(Role.MEMBER)
            val honorary = createUserWithRole(Role.MEMBER)
            val alumni = createUserWithRole(Role.MEMBER)

            val period = createContributionPeriodFixture()
            createMembership(regularUnpaid, MemberType.REGULAR, LocalDate.of(2024, 1, 1))
            createMembership(alreadyPaid, MemberType.REGULAR)
            createMembership(honorary, MemberType.HONORARY)
            createMembership(alumni, MemberType.ALUMNI)

            markPaid(alreadyPaid, period)

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val paymentDueDate = LocalDate.of(2024, 12, 31)

            mvc.perform(
                post("/contributionReminders/bulk/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        body(
                            listOf(
                                regularUnpaid.id!!,
                                alreadyPaid.id!!,
                                honorary.id!!,
                                alumni.id!!
                            ),
                            period.id!!,
                            cutoffDate,
                            paymentDueDate
                        )
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.counts.selected").value(4))
                .andExpect(jsonPath("$.counts.willApply").value(2)) // regular + alumni
                .andExpect(jsonPath("$.counts.warned").value(1)) // already-paid
                .andExpect(jsonPath("$.counts.excluded").value(1)) // honorary
        }

        @Test
        fun `returns not found when period is unknown`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/contributionReminders/bulk/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!), 999999, LocalDate.now(), LocalDate.now()))
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun `rejects empty selection`() {
            val board = createUserWithRole(Role.BOARD)
            val period = createContributionPeriodFixture()

            mvc.perform(
                post("/contributionReminders/bulk/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(emptyList(), period.id!!, LocalDate.now(), LocalDate.now()))
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class Execute {

        @Test
        fun `sends reminders to included members and writes audit rows`() {
            val board = createUserWithRole(Role.BOARD)
            val regular = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(regular, MemberType.REGULAR, LocalDate.of(2024, 1, 1))

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val paymentDueDate = LocalDate.of(2024, 12, 31)

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

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val paymentDueDate = LocalDate.of(2024, 12, 31)

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

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val paymentDueDate = LocalDate.of(2024, 12, 31)

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

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val paymentDueDate = LocalDate.of(2024, 12, 31)

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

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val paymentDueDate = LocalDate.of(2024, 12, 31)

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
    inner class Invariant {

        // The regression net for the class of bug this redesign targets: because preview
        // and execute share decideReminder, the preview's willApply/warned/excluded counts
        // must match execute's applied/skipped for an unchanged DB when every includable
        // row is included. See docs/proposals/bulk-actions/REDESIGN.md §7.
        @Test
        fun `preview willApply equals execute applied for an all-includable selection`() {
            val board = createUserWithRole(Role.BOARD)
            val regular = createUserWithRole(Role.MEMBER)
            val alumni = createUserWithRole(Role.MEMBER)
            val honorary = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(regular, MemberType.REGULAR, LocalDate.of(2024, 1, 1))
            createMembership(alumni, MemberType.ALUMNI)
            createMembership(honorary, MemberType.HONORARY)

            val userIds = listOf(regular.id!!, alumni.id!!, honorary.id!!)
            val cutoffDate = LocalDate.of(2024, 7, 1)
            val paymentDueDate = LocalDate.of(2024, 12, 31)

            // Preview: 2 includable (regular + alumni), 1 excluded (honorary).
            mvc.perform(
                post("/contributionReminders/bulk/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(userIds, period.id!!, cutoffDate, paymentDueDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.counts.willApply").value(2))
                .andExpect(jsonPath("$.counts.excluded").value(1))

            // Execute: applied must equal the preview's willApply; the excluded honorary
            // is skipped.
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
                            LocalDate.of(2024, 12, 31),
                            includedUserIds = setOf(honorary.id!!),
                            feeTypeOverrides = mapOf(honorary.id!! to BulkFeeType.FULL_YEAR_FEE),
                        )
                    )
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class Authorization {

        @Test
        fun `non-board is forbidden`() {
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()

            mvc.perform(
                post("/contributionReminders/bulk/preview")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!), period.id!!, LocalDate.now(), LocalDate.now()))
            )
                .andExpect(status().isForbidden)

            mvc.perform(
                post("/contributionReminders/bulk/execute")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!), period.id!!, LocalDate.now(), LocalDate.now()))
            )
                .andExpect(status().isForbidden)
        }
    }
}
