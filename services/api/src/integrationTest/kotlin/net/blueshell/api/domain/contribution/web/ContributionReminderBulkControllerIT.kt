package net.blueshell.api.domain.contribution.web

import net.blueshell.api.domain.contribution.persistence.ContributionReminder
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.EmailJobs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ContributionReminderBulkControllerIT :
    BulkEmailControllerITBase(
        executeEndpoint = "/contributionReminders/bulk/execute",
        previewEndpoint = "/contributionReminders/preview",
        dateParamName = "paymentDueDate",
    ) {

    @Nested
    inner class ReminderExecute {

        @Test
        fun `sends reminders to included members and writes audit rows`() {
            val board = createUserWithRole(Role.BOARD)
            val regular = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(regular, MemberType.REGULAR, LocalDate.of(2024, 1, 1), incasso = false)

            val cutoffDate = LocalDate.now()
            val paymentDueDate = LocalDate.now().plusDays(30)

            mvc.perform(
                post(executeEndpoint)
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
            createMembership(member, MemberType.REGULAR, LocalDate.of(2024, 1, 1), incasso = false)

            val cutoffDate = LocalDate.now()
            val paymentDueDate = LocalDate.now().plusDays(30)

            mvc.perform(
                post(executeEndpoint)
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
            createMembership(honorary, MemberType.HONORARY, incasso = false)

            val cutoffDate = LocalDate.now()
            val paymentDueDate = LocalDate.now().plusDays(30)

            mvc.perform(
                post(executeEndpoint)
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
            createMembership(member, MemberType.REGULAR, incasso = false)
            markPaid(member, period)

            val cutoffDate = LocalDate.now()
            val paymentDueDate = LocalDate.now().plusDays(30)

            // Execute without re-including: should skip
            mvc.perform(
                post(executeEndpoint)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!), period.id!!, cutoffDate, paymentDueDate))
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
            noEmail.email = ""
            persist(noEmail)

            val period = createContributionPeriodFixture()
            createMembership(noEmail, MemberType.REGULAR, incasso = false)

            val cutoffDate = LocalDate.now()
            val paymentDueDate = LocalDate.now().plusDays(30)

            mvc.perform(
                post(executeEndpoint)
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
    inner class ReminderPreview {

        @Test
        fun `returns non-empty subject and html and renders neither a reminder nor a job`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(member, MemberType.REGULAR, LocalDate.of(2024, 1, 1), incasso = false)

            val paymentDueDate = LocalDate.now().plusDays(30)

            mvc.perform(
                post(previewEndpoint)
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
                // Template images are inlined as data URIs so the iframe shows them regardless of hosting.
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
            createMembership(member, MemberType.REGULAR, LocalDate.of(2024, 1, 1), incasso = false)

            val paymentDueDate = LocalDate.now().plusDays(30)
            val formatted = paymentDueDate.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))

            mvc.perform(
                post(previewEndpoint)
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
}
