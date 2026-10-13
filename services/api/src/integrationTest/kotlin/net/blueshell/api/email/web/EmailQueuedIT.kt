package net.blueshell.api.email.web

import net.blueshell.api.auth.domain.AuthJobs
import net.blueshell.api.email.persistence.EmailRepository
import net.blueshell.api.event.domain.EventJobs
import net.blueshell.api.jobs.api.JobExecutor
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class EmailQueuedIT : UserTestSupport() {
    @Autowired
    private lateinit var emails: EmailRepository

    @Autowired
    private lateinit var executor: JobExecutor

    @Autowired
    private lateinit var jobs: JobQueue

    @Test
    fun `an email is in the log as queued with its job, and sent into the same record`() {
        val member = createUserWithRole(Role.MEMBER)
        mvc.perform(post("/recovery/password/reset/{username}", member.username)).andExpect(status().isNoContent)
        val job = findJobsByType(AuthJobs.Recovery.type).single()

        val queued = requireNotNull(emails.findTopByJobExecutionIdOrderByIdDesc(requireNotNull(job.id)))
        assertThat(queued.deliveryStatus).isEqualTo(EmailDeliveryStatus.QUEUED)
        assertThat(queued.recipientEmail).isEqualTo(member.email)
        assertThat(queued.subject).isNotBlank()

        executor.execute(job)
        val sent = emails.findById(queued.id!!).orElseThrow()
        assertThat(sent.deliveryStatus).isEqualTo(EmailDeliveryStatus.SENT)
        assertThat(emails.findAll().filter { it.jobExecutionId == job.id }).hasSize(1)
    }

    @Test
    fun `a recovery email is not made again, sent or bounced, even after the account's address changed`() {
        val member = createUserWithRole(Role.MEMBER)
        val board = createUserWithRole(Role.BOARD)
        mvc.perform(post("/recovery/password/reset/{username}", member.username)).andExpect(status().isNoContent)
        val job = findJobsByType(AuthJobs.Recovery.type).single()
        executor.execute(job)
        val sent = requireNotNull(emails.findTopByJobExecutionIdOrderByIdDesc(requireNotNull(job.id)))
        transactionTemplate.execute { userRepository.findById(member.id!!).orElseThrow().email = "moved-${member.email}" }

        mvc.perform(post("/management/emails/${sent.id}/resend").with(signedIn(board))).andExpect(status().isConflict)
        transactionTemplate.execute { emails.findById(sent.id!!).orElseThrow().deliveryStatus = EmailDeliveryStatus.BOUNCED }
        mvc.perform(post("/management/emails/${sent.id}/resend").with(signedIn(board))).andExpect(status().isConflict)

        assertThat(findJobsByType(AuthJobs.Recovery.type)).hasSize(1)
    }

    @Test
    fun `a bounced email is made again as a new queued email linked to it, once, and only by the board`() {
        val board = createUserWithRole(Role.BOARD)
        val payload = EventJobs.EventSignUpRemovedPayload("gordon@example.com", "Guest Gordon", "LAN Party")
        val queued = transactionTemplate.execute { jobs.runAsync(EventJobs.EventSignUpRemoved, payload, JobTrigger.SITE_ACTION) }
        val job = requireNotNull(queued)
        executor.execute(jobExecutions.findById(job.id!!).orElseThrow())
        val sent = requireNotNull(emails.findTopByJobExecutionIdOrderByIdDesc(job.id!!))

        mvc.perform(post("/management/emails/${sent.id}/resend").with(signedIn(board))).andExpect(status().isConflict)
        transactionTemplate.execute { emails.findById(sent.id!!).orElseThrow().deliveryStatus = EmailDeliveryStatus.BOUNCED }

        mvc
            .perform(post("/management/emails/${sent.id}/resend").with(signedIn(createUserWithRole(Role.MEMBER))))
            .andExpect(status().isForbidden)
        mvc
            .perform(post("/management/emails/${sent.id}/resend").with(signedIn(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resentFromId").value(sent.id!!))
            .andExpect(jsonPath("$.recipientEmail").value("gordon@example.com"))
            .andExpect(jsonPath("$.deliveryStatus").value("QUEUED"))
        mvc.perform(post("/management/emails/${sent.id}/resend").with(signedIn(board))).andExpect(status().isConflict)
        mvc
            .perform(get("/management/emails/${sent.id}").with(signedIn(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email.resendable").value(false))
            .andExpect(jsonPath("$.resends[0].deliveryStatus").value("QUEUED"))
        mvc
            .perform(get("/management/emails/${sent.id}").with(signedIn(createUserWithRole(Role.MEMBER))))
            .andExpect(status().isForbidden)
        mvc.perform(get("/management/emails/987654321").with(signedIn(board))).andExpect(status().isNotFound)
        assertThat(findJobsByType(EventJobs.EventSignUpRemoved.type)).hasSize(2)
    }
}
