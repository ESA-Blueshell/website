package net.blueshell.api.email.web

import net.blueshell.api.auth.domain.AuthJobs
import net.blueshell.api.email.persistence.EmailRepository
import net.blueshell.api.jobs.api.JobExecutor
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import net.blueshell.api.shared.enums.Role
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

    @Test
    fun `an email is in the log as queued with its job, sent into the same record, and resent to the current address`() {
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

        transactionTemplate.execute { userRepository.findById(member.id!!).orElseThrow().email = "moved-${member.email}" }
        val board = createUserWithRole(Role.BOARD)
        mvc
            .perform(post("/management/emails/${queued.id}/resend").with(signedIn(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resentFromId").value(queued.id!!))
            .andExpect(jsonPath("$.recipientEmail").value("moved-${member.email}"))
            .andExpect(jsonPath("$.deliveryStatus").value("QUEUED"))
        mvc
            .perform(get("/management/emails/${queued.id}").with(signedIn(board)))
            .andExpect(jsonPath("$.email.deliveryStatus").value("SENT"))
            .andExpect(jsonPath("$.resends[0].recipientEmail").value("moved-${member.email}"))
        val member2 = createUserWithRole(Role.MEMBER)
        mvc
            .perform(post("/management/emails/${queued.id}/resend").with(signedIn(member2)))
            .andExpect(status().isForbidden)
    }
}
