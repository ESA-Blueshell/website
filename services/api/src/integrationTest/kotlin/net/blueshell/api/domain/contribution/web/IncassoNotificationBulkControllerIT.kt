package net.blueshell.api.domain.contribution.web

import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.persistence.IncassoNotification
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.email.application.service.EmailSenderService
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.EmailJobs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class IncassoNotificationBulkControllerIT :
    BulkEmailControllerITBase(
        executeEndpoint = "/incassoNotifications/bulk/execute",
        previewEndpoint = "/incassoNotifications/preview",
        dateParamName = "expectedIncassoDate",
    ) {

    @Autowired
    private lateinit var emailSenderService: EmailSenderService

    private fun expectedAcademicYear(periodId: Long): String {
        val period = entityManager.find(ContributionPeriod::class.java, periodId)
        val startYear = period.startDate.year
        val endYear = period.endDate.year
        return if (endYear > startYear) "$startYear/$endYear" else "$startYear"
    }

    @Nested
    inner class IncassoExecute {

        @Test
        fun `sends notification, writes audit row and enqueues email`() {
            val board = createUserWithRole(Role.BOARD)
            val regular = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(regular, MemberType.REGULAR, LocalDate.of(2024, 1, 1), incasso = true)

            val cutoffDate = LocalDate.now()
            val expectedIncassoDate = LocalDate.now().plusDays(30)

            mvc.perform(
                post(executeEndpoint)
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

            val cutoffDate = LocalDate.now()
            val expectedIncassoDate = LocalDate.now().plusDays(30)

            mvc.perform(
                post(executeEndpoint)
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
            val formatted = expectedIncassoDate.format(
                DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", java.util.Locale.ENGLISH)
            )
            val refreshed = refreshUser(member)
            val academicYear = expectedAcademicYear(period.id!!)
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

            val cutoffDate = LocalDate.now()
            val expectedIncassoDate = LocalDate.now().plusDays(30)

            mvc.perform(
                post(executeEndpoint)
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

            val cutoffDate = LocalDate.now()
            val expectedIncassoDate = LocalDate.now().plusDays(30)

            // Execute without re-including: should skip
            mvc.perform(
                post(executeEndpoint)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!), period.id!!, cutoffDate, expectedIncassoDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(0))
                .andExpect(jsonPath("$.skipped").value(1))

            // Execute with re-including: should send
            mvc.perform(
                post(executeEndpoint)
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

            val cutoffDate = LocalDate.now()
            val expectedIncassoDate = LocalDate.now().plusDays(30)

            // Execute without re-including: should skip
            mvc.perform(
                post(executeEndpoint)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!), period.id!!, cutoffDate, expectedIncassoDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(0))
                .andExpect(jsonPath("$.skipped").value(1))

            // Execute with re-including: should send
            mvc.perform(
                post(executeEndpoint)
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
            noEmail.email = ""
            persist(noEmail)

            val period = createContributionPeriodFixture()
            createMembership(noEmail, MemberType.REGULAR, incasso = true)

            val cutoffDate = LocalDate.now()
            val expectedIncassoDate = LocalDate.now().plusDays(30)

            mvc.perform(
                post(executeEndpoint)
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
    inner class IncassoPreview {

        @Test
        fun `returns non-empty subject and html and renders neither a notification nor a job`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(member, MemberType.REGULAR, LocalDate.of(2024, 1, 1), incasso = true)

            val expectedIncassoDate = LocalDate.now().plusDays(30)

            mvc.perform(
                post(previewEndpoint)
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

            val expectedIncassoDate = LocalDate.now().plusDays(30)
            val formatted = expectedIncassoDate.format(
                DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", java.util.Locale.ENGLISH)
            )

            mvc.perform(
                post(previewEndpoint)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(previewBody(member.id!!, period.id!!, BulkFeeType.ALUMNI_FEE, expectedIncassoDate))
            )
                .andExpect(status().isOk)
                .andExpect(
                    jsonPath("$.html").value(org.hamcrest.Matchers.containsString("%.2f".format(period.alumniFee)))
                )
                .andExpect(jsonPath("$.html").value(org.hamcrest.Matchers.containsString(formatted)))
                .andExpect(
                    jsonPath("$.html").value(
                        org.hamcrest.Matchers.containsString("the alumni fee, as you are an alumni member")
                    )
                )
        }
    }
}
