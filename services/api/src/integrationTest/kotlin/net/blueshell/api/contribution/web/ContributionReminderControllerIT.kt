package net.blueshell.api.contribution.web

import net.blueshell.api.contribution.persistence.ContributionReminder
import net.blueshell.api.contribution.persistence.ContributionReminderRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.EmailJobs
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class ContributionReminderControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var reminderRepository: ContributionReminderRepository

    private fun payload(userId: Long, periodId: Long): String =
        """{"userId":$userId,"contributionPeriodId":$periodId}"""

    private fun batchPayload(firstUserId: Long, secondUserId: Long, periodId: Long): String =
        """[{"userId":$firstUserId,"contributionPeriodId":$periodId},{"userId":$secondUserId,"contributionPeriodId":$periodId}]"""

    @Nested
    inner class SendContributionReminder {

        @Test
        fun `sends reminder and schedules email job`() {
            val board = createUserWithRole(Role.BOARD)
            val user = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()

            mvc.perform(
                post("/contributionReminders")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload(user.id!!, period.id!!))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.userId").value(user.id))
                .andExpect(jsonPath("$.contributionPeriodId").value(period.id))

            val jobs = findJobsByType(EmailJobs.ContributionReminder.type)
            assertThat(jobs).hasSize(1)
            // The job names the ask, because a member can be asked for the same period more
            // than once and the pair no longer names one row.
            val ask = reminderRepository.findByContributionPeriod_Id(period.id!!).single()
            assertThat(jobs.first().payload).contains("\"contributionReminderId\":${ask.id}")
        }

        /**
         * The treasurer chases one member as well as a hundred, so the same member and period
         * can be asked again — and each ask is its own row and its own email.
         */
        @Test
        fun `asking the same member again records another ask`() {
            val board = createUserWithRole(Role.BOARD)
            val user = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()

            repeat(3) {
                mvc.perform(
                    post("/contributionReminders")
                        .with(bearer(board))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(user.id!!, period.id!!))
                )
                    .andExpect(status().isCreated)
            }

            assertThat(reminderRepository.findByContributionPeriod_Id(period.id!!)).hasSize(3)
            assertThat(findJobsByType(EmailJobs.ContributionReminder.type)).hasSize(3)
        }

        @Test
        fun `returns bad request for invalid payload`() {
            val board = createUserWithRole(Role.BOARD)
            val period = createContributionPeriodFixture()

            mvc.perform(
                post("/contributionReminders")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"contributionPeriodId":${period.id}}""")
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class SendContributionReminderBatch {

        @Test
        fun `sends batch reminders`() {
            val board = createUserWithRole(Role.BOARD)
            val user1 = createUserWithRole(Role.MEMBER)
            val user2 = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()

            mvc.perform(
                post("/contributionReminders/batch")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchPayload(user1.id!!, user2.id!!, period.id!!))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$[0].contributionPeriodId").value(period.id))
                .andExpect(jsonPath("$[1].contributionPeriodId").value(period.id))

            val jobs = findJobsByType(EmailJobs.ContributionReminder.type)
            assertThat(jobs).hasSize(2)
        }
    }

    @Nested
    inner class FindContributionReminders {

        @Test
        fun `lists reminders for contribution period`() {
            val board = createUserWithRole(Role.BOARD)
            val user = createUserWithRole(Role.MEMBER)
            val period = createContributionPeriodFixture()
            persist(
                ContributionReminder(
                user = user,
                    contributionPeriod = period,
                )
            )

            mvc.perform(
                get("/contributionReminders")
                    .with(bearer(board))
                    .param("contributionPeriodId", period.id!!.toString())
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$[0].userId").value(user.id))
                .andExpect(jsonPath("$[0].contributionPeriodId").value(period.id))
        }

        @Test
        fun `returns not found when period is unknown`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                get("/contributionReminders")
                    .with(bearer(board))
                    .param("contributionPeriodId", "999999")
            )
                .andExpect(status().isNotFound)
        }
    }
}
