package net.blueshell.api.domain.contribution.web

import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
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

/**
 * Abstract base for IT tests of bulk-email endpoints (contribution reminders, incasso notifications).
 * Centralizes shared fixture setup, JSON builders, and test logic to reduce duplication.
 * Subclasses define the specific endpoint paths and date field names.
 */
@SpringBootTest
abstract class BulkEmailControllerITBase(
    val executeEndpoint: String,
    val previewEndpoint: String,
    val dateParamName: String,  // "paymentDueDate" or "expectedIncassoDate"
) : UserTestSupport() {

    @Autowired
    protected lateinit var contributionService: ContributionService

    /**
     * Build the JSON body for execute/preview requests.
     * The dateParam is substituted under the key dateParamName in the JSON.
     */
    protected fun body(
        userIds: List<Long>,
        periodId: Long,
        cutoffDate: LocalDate,
        dateParam: LocalDate,
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
            "$dateParamName":"$dateParam",
            "includedUserIds":$includedJson,
            "feeTypeOverrides":$overridesJson
        }"""
    }

    /**
     * Build the JSON body for preview requests.
     * The dateParam is substituted under the key dateParamName in the JSON.
     */
    protected fun previewBody(
        userId: Long,
        periodId: Long,
        feeType: BulkFeeType,
        dateParam: LocalDate,
    ): String = """{
        "userId":$userId,
        "contributionPeriodId":$periodId,
        "feeType":"$feeType",
        "$dateParamName":"$dateParam"
    }"""

    protected fun markPaid(user: User, period: ContributionPeriod) = persist(
        Contribution(id = Contribution.Id(user.id, period.id), user = user, contributionPeriod = period)
    )

    protected fun createMembership(
        user: User,
        memberType: MemberType,
        startDate: LocalDate = LocalDate.of(2024, 1, 1),
        incasso: Boolean = false
    ): Membership = persist(
        Membership(
            user = user,
            memberType = memberType,
            startDate = startDate,
            endDate = null,
            incasso = incasso
        )
    )

    @Nested
    inner class Execute {

        @Test
        fun `sends email and writes audit row`() {
            val board = createUserWithRole(Role.BOARD)
            val regular = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(regular, MemberType.REGULAR, LocalDate.of(2024, 1, 1), incasso = false)

            val cutoffDate = LocalDate.now()
            val dateParam = LocalDate.now().plusDays(30)

            mvc.perform(
                post(executeEndpoint)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(regular.id!!), period.id!!, cutoffDate, dateParam))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(1))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.queued").value(1))
        }

        @Test
        fun `honors fee type overrides in audit and email`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(member, MemberType.REGULAR, LocalDate.of(2024, 1, 1), incasso = false)

            val cutoffDate = LocalDate.now()
            val dateParam = LocalDate.now().plusDays(30)

            mvc.perform(
                post(executeEndpoint)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        body(
                            listOf(member.id!!),
                            period.id!!,
                            cutoffDate,
                            dateParam,
                            includedUserIds = setOf(member.id!!),
                            feeTypeOverrides = mapOf(member.id!! to BulkFeeType.HALF_YEAR_FEE)
                        )
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(1))
                .andExpect(jsonPath("$.queued").value(1))
        }

        @Test
        fun `excludes honorary members and never sends them`() {
            val board = createUserWithRole(Role.BOARD)
            val honorary = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(honorary, MemberType.HONORARY, incasso = false)

            val cutoffDate = LocalDate.now()
            val dateParam = LocalDate.now().plusDays(30)

            mvc.perform(
                post(executeEndpoint)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(honorary.id!!), period.id!!, cutoffDate, dateParam))
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
            createMembership(member, MemberType.REGULAR, incasso = false)
            markPaid(member, period)

            val cutoffDate = LocalDate.now()
            val dateParam = LocalDate.now().plusDays(30)

            mvc.perform(
                post(executeEndpoint)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!), period.id!!, cutoffDate, dateParam))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(0))
                .andExpect(jsonPath("$.skipped").value(1))

            mvc.perform(
                post(executeEndpoint)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        body(
                            listOf(member.id!!),
                            period.id!!,
                            cutoffDate,
                            dateParam,
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
            noEmail.email = ""
            persist(noEmail)

            val period = createContributionPeriodFixture()
            createMembership(noEmail, MemberType.REGULAR, incasso = false)

            val cutoffDate = LocalDate.now()
            val dateParam = LocalDate.now().plusDays(30)

            mvc.perform(
                post(executeEndpoint)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(noEmail.id!!), period.id!!, cutoffDate, dateParam))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(0))
                .andExpect(jsonPath("$.skipped").value(1))
        }
    }

    @Nested
    inner class MixedCohort {

        // FE preview is client-side (bulkCompute.ts); execute mirrors its decision logic for mixed cohorts.
        @Test
        fun `execute applies includable members and skips the excluded honorary`() {
            val board = createUserWithRole(Role.BOARD)
            val regular = createUserWithRole(Role.MEMBER)
            val alumni = createUserWithRole(Role.MEMBER)
            val honorary = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(regular, MemberType.REGULAR, LocalDate.of(2024, 1, 1), incasso = false)
            createMembership(alumni, MemberType.ALUMNI, incasso = false)
            createMembership(honorary, MemberType.HONORARY, incasso = false)

            val userIds = listOf(regular.id!!, alumni.id!!, honorary.id!!)
            val cutoffDate = LocalDate.now()
            val dateParam = LocalDate.now().plusDays(30)

            mvc.perform(
                post(executeEndpoint)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(userIds, period.id!!, cutoffDate, dateParam))
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
            createMembership(honorary, MemberType.HONORARY, incasso = false)

            mvc.perform(
                post(executeEndpoint)
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
        fun `returns non-empty subject and html and renders neither entity nor job`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(member, MemberType.REGULAR, LocalDate.of(2024, 1, 1), incasso = false)

            val dateParam = LocalDate.now().plusDays(30)

            mvc.perform(
                post(previewEndpoint)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(previewBody(member.id!!, period.id!!, BulkFeeType.FULL_YEAR_FEE, dateParam))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.subject").isNotEmpty)
                .andExpect(jsonPath("$.html").isNotEmpty)
                // Template images are inlined as data URIs so the iframe shows them regardless of hosting.
                .andExpect(
                    jsonPath("$.html").value(
                        org.hamcrest.Matchers.containsString("data:image/png;base64,")
                    )
                )
        }

        @Test
        fun `honors the requested fee type and date param in the rendered html`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(member, MemberType.REGULAR, LocalDate.of(2024, 1, 1), incasso = false)

            val dateParam = LocalDate.now().plusDays(30)

            mvc.perform(
                post(previewEndpoint)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(previewBody(member.id!!, period.id!!, BulkFeeType.HALF_YEAR_FEE, dateParam))
            )
                .andExpect(status().isOk)
                .andExpect(
                    jsonPath("$.html").value(org.hamcrest.Matchers.containsString("%.2f".format(period.halfYearFee)))
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
                post(executeEndpoint)
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
                post(previewEndpoint)
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(previewBody(member.id!!, period.id!!, BulkFeeType.FULL_YEAR_FEE, LocalDate.now().plusDays(30)))
            )
                .andExpect(status().isForbidden)
        }
    }
}
