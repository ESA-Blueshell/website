package net.blueshell.api.domain.contribution.web

import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.persistence.IncassoNotification
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.email.application.service.EmailSenderService
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
class IncassoNotificationBulkControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var emailSenderService: EmailSenderService

    private fun body(
        userIds: List<Long>,
        periodId: Long,
        cutoffDate: LocalDate,
        expectedIncassoDate: LocalDate,
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
            "expectedIncassoDate":"$expectedIncassoDate",
            "includedUserIds":$includedJson,
            "feeTypeOverrides":$overridesJson
        }"""
    }

    private fun previewBody(
        userId: Long,
        periodId: Long,
        feeType: BulkFeeType,
        expectedIncassoDate: LocalDate,
    ): String = """{
        "userId":$userId,
        "contributionPeriodId":$periodId,
        "feeType":"$feeType",
        "expectedIncassoDate":"$expectedIncassoDate"
    }"""

    private fun markPaid(user: User, period: ContributionPeriod) = persist(
        Contribution(id = Contribution.Id(user.id, period.id), user = user, contributionPeriod = period)
    )

    private fun createMembership(
        user: User,
        memberType: MemberType,
        startDate: LocalDate = LocalDate.of(2024, 1, 1),
        incasso: Boolean = true
    ): Membership = persist(
        Membership(
            user = user,
            memberType = memberType,
            startDate = startDate,
            endDate = null,
            incasso = incasso
        )
    )

    private fun expectedAcademicYear(period: ContributionPeriod): String {
        val startYear = period.startDate.year
        val endYear = period.endDate.year
        return if (endYear > startYear) "$startYear/$endYear" else "$startYear"
    }

    @Nested
    inner class Execute {

        @Test
        fun `sends notification, writes audit row and enqueues email`() {
            val board = createUserWithRole(Role.BOARD)
            val regular = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(regular, MemberType.REGULAR, LocalDate.of(2024, 1, 1), incasso = true)

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val expectedIncassoDate = LocalDate.of(2024, 12, 31)

            mvc.perform(
                post("/incassoNotifications/bulk/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(regular.id!!), period.id!!, cutoffDate, expectedIncassoDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(1))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.queued").value(1))

            // Verify audit record was written
            transactionTemplate.execute {
                entityManager.clear()
                val notification = entityManager.find(
                    IncassoNotification::class.java,
                    IncassoNotification.Id(regular.id, period.id)
                )
                assertThat(notification).isNotNull
                assertThat(notification.amount).isEqualTo(period.fullYearFee)
                assertThat(notification.expectedIncassoDate).isEqualTo(expectedIncassoDate)
            }

            // Verify email job enqueued with correct payload
            val jobs = findJobsByType(EmailJobs.IncassoNotification.type)
            assertThat(jobs).hasSize(1)
            assertThat(jobs.first().payload)
                .contains("\"userId\":${regular.id}")
                .contains("\"contributionPeriodId\":${period.id}")
        }

        @Test
        fun `honors fee type overrides in audit and email`() {
            // Member started before cutoff → default would be FULL_YEAR_FEE; override to ALUMNI_FEE
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(member, MemberType.REGULAR, LocalDate.of(2024, 1, 1), incasso = true)

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val expectedIncassoDate = LocalDate.of(2024, 12, 31)

            mvc.perform(
                post("/incassoNotifications/bulk/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        body(
                            listOf(member.id!!),
                            period.id!!,
                            cutoffDate,
                            expectedIncassoDate,
                            // The member is INCLUDED, so a real client sends them in includedUserIds
                            // (FE set = INCLUDED ∪ re-included WARNING); overrides require membership there.
                            includedUserIds = setOf(member.id!!),
                            feeTypeOverrides = mapOf(member.id!! to BulkFeeType.ALUMNI_FEE)
                        )
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(1))
                .andExpect(jsonPath("$.queued").value(1))

            // Verify audit record has the alumni fee amount (from the override)
            transactionTemplate.execute {
                entityManager.clear()
                val notification = entityManager.find(
                    IncassoNotification::class.java,
                    IncassoNotification.Id(member.id, period.id)
                )
                assertThat(notification).isNotNull
                assertThat(notification.amount).isEqualTo(period.alumniFee)
            }

            // Verify rendered mail carries the overridden amount and formatted incasso date
            emailTransportClient.reset()
            emailSenderService.sendIncassoNotificationEmail(member.id!!, period.id!!)
            val formatted = expectedIncassoDate.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))
            val refreshed = refreshUser(member)
            val academicYear = expectedAcademicYear(period)
            assertEmailSent(
                toEmail = refreshed.email,
                subject = "Your Blueshell contribution will be collected automatically ($academicYear)",
                bodyContains = "%.2f".format(period.alumniFee)
            )
            assertThat(emailTransportClient.sentEmails.first().htmlContent).contains(formatted)
        }

        @Test
        fun `excludes honorary members and never sends them`() {
            val board = createUserWithRole(Role.BOARD)
            val honorary = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(honorary, MemberType.HONORARY, incasso = true)

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val expectedIncassoDate = LocalDate.of(2024, 12, 31)

            mvc.perform(
                post("/incassoNotifications/bulk/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(honorary.id!!), period.id!!, cutoffDate, expectedIncassoDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(0))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.queued").value(0))

            assertThat(findJobsByType(EmailJobs.IncassoNotification.type)).isEmpty()
        }

        @Test
        fun `incasso-mismatch excluded by default but sent when re-included`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(member, MemberType.REGULAR, LocalDate.of(2024, 1, 1), incasso = false)

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val expectedIncassoDate = LocalDate.of(2024, 12, 31)

            // Execute without re-including: should skip
            mvc.perform(
                post("/incassoNotifications/bulk/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!), period.id!!, cutoffDate, expectedIncassoDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(0))
                .andExpect(jsonPath("$.skipped").value(1))

            // Execute with re-including: should send
            mvc.perform(
                post("/incassoNotifications/bulk/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        body(
                            listOf(member.id!!),
                            period.id!!,
                            cutoffDate,
                            expectedIncassoDate,
                            includedUserIds = setOf(member.id!!)
                        )
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(1))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.queued").value(1))
        }

        @Test
        fun `already-paid excluded by default but sent when re-included`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(member, MemberType.REGULAR, incasso = true)
            markPaid(member, period)

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val expectedIncassoDate = LocalDate.of(2024, 12, 31)

            // Execute without re-including: should skip
            mvc.perform(
                post("/incassoNotifications/bulk/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!), period.id!!, cutoffDate, expectedIncassoDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(0))
                .andExpect(jsonPath("$.skipped").value(1))

            // Execute with re-including: should send
            mvc.perform(
                post("/incassoNotifications/bulk/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        body(
                            listOf(member.id!!),
                            period.id!!,
                            cutoffDate,
                            expectedIncassoDate,
                            includedUserIds = setOf(member.id!!)
                        )
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(1))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.queued").value(1))
        }

        @Test
        fun `skips members without email`() {
            val board = createUserWithRole(Role.BOARD)
            val noEmail = createUserWithRole(Role.MEMBER)
            noEmail.email = "" // Clear email
            persist(noEmail)

            val period = createContributionPeriodFixture()
            createMembership(noEmail, MemberType.REGULAR, incasso = true)

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val expectedIncassoDate = LocalDate.of(2024, 12, 31)

            mvc.perform(
                post("/incassoNotifications/bulk/execute")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(noEmail.id!!), period.id!!, cutoffDate, expectedIncassoDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(0))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.queued").value(0))
        }
    }

    @Nested
    inner class Preview {

        @Test
        fun `returns non-empty subject and html and renders neither a notification nor a job`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(member, MemberType.REGULAR, LocalDate.of(2024, 1, 1), incasso = true)

            val expectedIncassoDate = LocalDate.of(2024, 12, 31)

            mvc.perform(
                post("/incassoNotifications/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(previewBody(member.id!!, period.id!!, BulkFeeType.FULL_YEAR_FEE, expectedIncassoDate))
            )
                .andExpect(status().isOk)
                .andExpect(
                    jsonPath("$.subject").value(
                        org.hamcrest.Matchers.containsString("Your Blueshell contribution will be collected automatically")
                    )
                )
                .andExpect(jsonPath("$.subject").isNotEmpty)
                .andExpect(jsonPath("$.html").isNotEmpty)

            // Preview must not persist a notification …
            transactionTemplate.execute {
                entityManager.clear()
                val notification = entityManager.find(
                    IncassoNotification::class.java,
                    IncassoNotification.Id(member.id, period.id)
                )
                assertThat(notification).isNull()
            }
            // … nor enqueue a send.
            assertThat(findJobsByType(EmailJobs.IncassoNotification.type)).isEmpty()
        }

        @Test
        fun `honors the requested fee type and expected incasso date in the rendered html`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(member, MemberType.REGULAR, LocalDate.of(2024, 1, 1), incasso = true)

            val expectedIncassoDate = LocalDate.of(2024, 12, 31)
            val formatted = expectedIncassoDate.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))

            mvc.perform(
                post("/incassoNotifications/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(previewBody(member.id!!, period.id!!, BulkFeeType.ALUMNI_FEE, expectedIncassoDate))
            )
                .andExpect(status().isOk)
                .andExpect(
                    jsonPath("$.html").value(org.hamcrest.Matchers.containsString("%.2f".format(period.alumniFee)))
                )
                .andExpect(jsonPath("$.html").value(org.hamcrest.Matchers.containsString(formatted)))
        }
    }

    @Nested
    inner class Authorization {

        @Test
        fun `non-board is forbidden`() {
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()

            mvc.perform(
                post("/incassoNotifications/bulk/execute")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!), period.id!!, LocalDate.now(), LocalDate.now()))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `non-board is forbidden from preview`() {
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()

            mvc.perform(
                post("/incassoNotifications/preview")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(previewBody(member.id!!, period.id!!, BulkFeeType.FULL_YEAR_FEE, LocalDate.now()))
            )
                .andExpect(status().isForbidden)
        }
    }
}
