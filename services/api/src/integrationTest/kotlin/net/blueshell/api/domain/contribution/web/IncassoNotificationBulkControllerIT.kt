package net.blueshell.api.domain.contribution.web

import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.contribution.persistence.IncassoNotification
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.platform.integration.email.application.service.EmailSenderService
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
        amountOverrides: Map<Long, Double> = emptyMap()
    ): String {
        val includedJson = if (includedUserIds.isEmpty()) "[]" else includedUserIds.joinToString(",", "[", "]")
        val overridesJson = if (amountOverrides.isEmpty()) "{}" else {
            amountOverrides.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":$v" }
        }
        return """{
            "userIds":[${userIds.joinToString(",")}],
            "contributionPeriodId":$periodId,
            "cutoffDate":"$cutoffDate",
            "expectedIncassoDate":"$expectedIncassoDate",
            "includedUserIds":$includedJson,
            "amountOverrides":$overridesJson
        }"""
    }

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

    @Nested
    inner class Preview {

        @Test
        fun `regular incasso member starting before cutoff resolves full-year fee`() {
            val board = createUserWithRole(Role.BOARD)
            val regular = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(regular, MemberType.REGULAR, LocalDate.of(2024, 1, 1), incasso = true)

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val expectedIncassoDate = LocalDate.of(2024, 12, 31)

            mvc.perform(
                post("/incassoNotifications/bulk/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(regular.id!!), period.id!!, cutoffDate, expectedIncassoDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.counts.selected").value(1))
                .andExpect(jsonPath("$.counts.willApply").value(1))
                .andExpect(jsonPath("$.counts.excluded").value(0))
                .andExpect(jsonPath("$.rows[0].amount").value(period.fullYearFee))
                .andExpect(jsonPath("$.rows[0].disposition").value("INCLUDED"))
                .andExpect(jsonPath("$.rows[0].memberType").value("REGULAR"))
        }

        @Test
        fun `regular incasso member starting after cutoff resolves half-year fee`() {
            val board = createUserWithRole(Role.BOARD)
            val regular = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(regular, MemberType.REGULAR, LocalDate.of(2024, 8, 1), incasso = true)

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val expectedIncassoDate = LocalDate.of(2024, 12, 31)

            mvc.perform(
                post("/incassoNotifications/bulk/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(regular.id!!), period.id!!, cutoffDate, expectedIncassoDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.rows[0].amount").value(period.halfYearFee))
                .andExpect(jsonPath("$.rows[0].disposition").value("INCLUDED"))
        }

        @Test
        fun `alumni incasso member resolves alumni fee`() {
            val board = createUserWithRole(Role.BOARD)
            val alumni = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(alumni, MemberType.ALUMNI, LocalDate.of(2023, 1, 1), incasso = true)

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val expectedIncassoDate = LocalDate.of(2024, 12, 31)

            mvc.perform(
                post("/incassoNotifications/bulk/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(alumni.id!!), period.id!!, cutoffDate, expectedIncassoDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.rows[0].amount").value(period.alumniFee))
                .andExpect(jsonPath("$.rows[0].disposition").value("INCLUDED"))
                .andExpect(jsonPath("$.rows[0].memberType").value("ALUMNI"))
        }

        @Test
        fun `honorary member is excluded and shown red`() {
            val board = createUserWithRole(Role.BOARD)
            val honorary = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(honorary, MemberType.HONORARY, incasso = true)

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val expectedIncassoDate = LocalDate.of(2024, 12, 31)

            mvc.perform(
                post("/incassoNotifications/bulk/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(honorary.id!!), period.id!!, cutoffDate, expectedIncassoDate))
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
        fun `member without incasso is a warning with INCASSO_MISMATCH`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(member, MemberType.REGULAR, LocalDate.of(2024, 1, 1), incasso = false)

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val expectedIncassoDate = LocalDate.of(2024, 12, 31)

            mvc.perform(
                post("/incassoNotifications/bulk/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!), period.id!!, cutoffDate, expectedIncassoDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.counts.warned").value(1))
                .andExpect(jsonPath("$.counts.willApply").value(0))
                .andExpect(jsonPath("$.rows[0].disposition").value("WARNING"))
                .andExpect(jsonPath("$.rows[0].reason").value("INCASSO_MISMATCH"))
                .andExpect(jsonPath("$.rows[0].amount").value(period.fullYearFee))
        }

        @Test
        fun `already-paid incasso member is a warning with ALREADY_PAID`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(member, MemberType.REGULAR, incasso = true)
            markPaid(member, period)

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val expectedIncassoDate = LocalDate.of(2024, 12, 31)

            mvc.perform(
                post("/incassoNotifications/bulk/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!), period.id!!, cutoffDate, expectedIncassoDate))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.counts.warned").value(1))
                .andExpect(jsonPath("$.counts.willApply").value(0))
                .andExpect(jsonPath("$.rows[0].disposition").value("WARNING"))
                .andExpect(jsonPath("$.rows[0].reason").value("ALREADY_PAID"))
                .andExpect(jsonPath("$.rows[0].amount").value(period.fullYearFee))
        }

        @Test
        fun `mixed selection resolves correct counts`() {
            val board = createUserWithRole(Role.BOARD)
            val regularIncasso = createUserWithRole(Role.MEMBER)
            val noIncasso = createUserWithRole(Role.MEMBER)
            val alreadyPaid = createUserWithRole(Role.MEMBER)
            val honorary = createUserWithRole(Role.MEMBER)
            val alumni = createUserWithRole(Role.MEMBER)

            val period = createContributionPeriodFixture()
            createMembership(regularIncasso, MemberType.REGULAR, LocalDate.of(2024, 1, 1), incasso = true)
            createMembership(noIncasso, MemberType.REGULAR, LocalDate.of(2024, 1, 1), incasso = false)
            createMembership(alreadyPaid, MemberType.REGULAR, incasso = true)
            createMembership(honorary, MemberType.HONORARY, incasso = true)
            createMembership(alumni, MemberType.ALUMNI, incasso = true)

            markPaid(alreadyPaid, period)

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val expectedIncassoDate = LocalDate.of(2024, 12, 31)

            mvc.perform(
                post("/incassoNotifications/bulk/preview")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        body(
                            listOf(
                                regularIncasso.id!!,
                                noIncasso.id!!,
                                alreadyPaid.id!!,
                                honorary.id!!,
                                alumni.id!!
                            ),
                            period.id!!,
                            cutoffDate,
                            expectedIncassoDate
                        )
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.counts.selected").value(5))
                .andExpect(jsonPath("$.counts.willApply").value(2)) // regularIncasso + alumni
                .andExpect(jsonPath("$.counts.warned").value(2)) // noIncasso + alreadyPaid
                .andExpect(jsonPath("$.counts.excluded").value(1)) // honorary
        }

        @Test
        fun `returns not found when period is unknown`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/incassoNotifications/bulk/preview")
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
                post("/incassoNotifications/bulk/preview")
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
        fun `honors amount overrides in audit and email`() {
            val board = createUserWithRole(Role.BOARD)
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            createMembership(member, MemberType.REGULAR, LocalDate.of(2024, 1, 1), incasso = true)

            val cutoffDate = LocalDate.of(2024, 7, 1)
            val expectedIncassoDate = LocalDate.of(2024, 12, 31)
            val overriddenAmount = 75.50

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
                            amountOverrides = mapOf(member.id!! to overriddenAmount)
                        )
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.applied").value(1))
                .andExpect(jsonPath("$.queued").value(1))

            // Verify audit record has overridden amount
            transactionTemplate.execute {
                entityManager.clear()
                val notification = entityManager.find(
                    IncassoNotification::class.java,
                    IncassoNotification.Id(member.id, period.id)
                )
                assertThat(notification).isNotNull
                assertThat(notification.amount).isEqualTo(overriddenAmount)
            }

            // Verify rendered mail carries the overridden amount and formatted incasso date
            emailTransportClient.reset()
            emailSenderService.sendIncassoNotificationEmail(member.id!!, period.id!!)
            val formatted = expectedIncassoDate.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))
            val refreshed = refreshUser(member)
            assertEmailSent(
                toEmail = refreshed.email,
                subject = "Membership Contribution Collection Notice - Blueshell Esports",
                bodyContains = "%.2f".format(overriddenAmount)
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
    inner class Authorization {

        @Test
        fun `non-board is forbidden`() {
            val member = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()

            mvc.perform(
                post("/incassoNotifications/bulk/preview")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!), period.id!!, LocalDate.now(), LocalDate.now()))
            )
                .andExpect(status().isForbidden)

            mvc.perform(
                post("/incassoNotifications/bulk/execute")
                    .with(bearer(member))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(listOf(member.id!!), period.id!!, LocalDate.now(), LocalDate.now()))
            )
                .andExpect(status().isForbidden)
        }
    }
}
