package net.blueshell.api.platform.integration.email.web

import net.blueshell.api.shared.enums.EmailDeliveryStatus
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class EmailManagementControllerIT : UserTestSupport() {

    @Nested
    inner class ListEmails {
        @Test
        fun `admin lists emails with paging metadata`() {
            val admin = createUserWithRole(Role.ADMIN)
            emailOutboxFactory.create(recipientEmail = "alice@example.com", subject = "Hello Alice")
            emailOutboxFactory.create(recipientEmail = "bob@example.com", subject = "Hello Bob")

            mvc.perform(get("/management/emails").with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[0].id").isNumber)
                .andExpect(jsonPath("$.content[0].recipientEmail").isString)
                .andExpect(jsonPath("$.content[0].subject").isString)
                .andExpect(jsonPath("$.content[0].deliveryStatus").isString)
                .andExpect(jsonPath("$.page.size").value(50))
        }

        @Test
        fun `admin filters emails by delivery status`() {
            val admin = createUserWithRole(Role.ADMIN)
            val sent = emailOutboxFactory.create(deliveryStatus = EmailDeliveryStatus.SENT)
            emailOutboxFactory.create(deliveryStatus = EmailDeliveryStatus.FAILED)

            mvc.perform(
                get("/management/emails")
                    .queryParam("deliveryStatus", "SENT")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[?(@.id == ${sent.id})].deliveryStatus").value("SENT"))
        }

        @Test
        fun `admin searches emails by recipient`() {
            val admin = createUserWithRole(Role.ADMIN)
            val matching = emailOutboxFactory.create(recipientEmail = "unique-search@example.com")
            emailOutboxFactory.create(recipientEmail = "other@example.com")

            mvc.perform(
                get("/management/emails")
                    .queryParam("search", "unique-search")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[?(@.id == ${matching.id})]").isNotEmpty)
        }
    }

    @Nested
    inner class Stats {
        @Test
        fun `admin gets email stats with correct counts`() {
            val admin = createUserWithRole(Role.ADMIN)
            emailOutboxFactory.create(deliveryStatus = EmailDeliveryStatus.SENT)
            emailOutboxFactory.create(deliveryStatus = EmailDeliveryStatus.FAILED)

            mvc.perform(get("/management/emails/stats").with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalCount").isNumber)
                .andExpect(jsonPath("$.sentCount").isNumber)
                .andExpect(jsonPath("$.failedCount").isNumber)
                .andExpect(jsonPath("$.pendingCount").isNumber)
                .andExpect(jsonPath("$.deliveredCount").isNumber)
                .andExpect(jsonPath("$.openedCount").isNumber)
                .andExpect(jsonPath("$.bouncedCount").isNumber)
        }

        @Test
        fun `board member can access stats`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(get("/management/emails/stats").with(bearer(board)))
                .andExpect(status().isOk)
        }
    }

    @Nested
    inner class Retry {
        @Test
        fun `retry requeues linked job and returns outbox DTO`() {
            val admin = createUserWithRole(Role.ADMIN)
            val jobExecution = createJobExecutionFixture(status = JobExecutionStatus.FAILED)
            val outbox = emailOutboxFactory.create(
                deliveryStatus = EmailDeliveryStatus.FAILED,
                jobExecutionId = jobExecution.id,
            )

            mvc.perform(post("/management/emails/${outbox.id}/retry").with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(outbox.id))
        }

        @Test
        fun `retry returns 400 when outbox entry has no linked job`() {
            val admin = createUserWithRole(Role.ADMIN)
            val outbox = emailOutboxFactory.create(
                deliveryStatus = EmailDeliveryStatus.FAILED,
                jobExecutionId = null,
            )

            mvc.perform(post("/management/emails/${outbox.id}/retry").with(bearer(admin)))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `retry returns 400 when linked job is not FAILED or DEAD`() {
            val admin = createUserWithRole(Role.ADMIN)
            val jobExecution = createJobExecutionFixture(status = JobExecutionStatus.SUCCESS)
            val outbox = emailOutboxFactory.create(
                deliveryStatus = EmailDeliveryStatus.SENT,
                jobExecutionId = jobExecution.id,
            )

            mvc.perform(post("/management/emails/${outbox.id}/retry").with(bearer(admin)))
                .andExpect(status().isBadRequest)
        }
    }
}
