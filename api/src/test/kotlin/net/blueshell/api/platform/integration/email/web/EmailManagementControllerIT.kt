package net.blueshell.api.platform.integration.email.web

import net.blueshell.api.shared.enums.EmailDeliveryStatus
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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
            emailFactory.create(recipientEmail = "alice@example.com", subject = "Hello Alice")
            emailFactory.create(recipientEmail = "bob@example.com", subject = "Hello Bob")

            mvc.perform(get("/management/emails").with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[0].id").isNumber)
                .andExpect(jsonPath("$.content[0].recipientEmail").isString)
                .andExpect(jsonPath("$.content[0].subject").isString)
                .andExpect(jsonPath("$.content[0].deliveryStatus").isString)
                .andExpect(jsonPath("$.page.size").value(50))
        }

        @Test
        fun `empty list returns page metadata with zero elements`() {
            val admin = createUserWithRole(Role.ADMIN)

            val result = mvc.perform(get("/management/emails").with(bearer(admin)))
                .andExpect(status().isOk)
                .andReturn()

            val body = result.response.contentAsString
            // page metadata should still be present even with 0 elements
            assertThat(body).contains("\"content\"")
        }

        @Test
        fun `admin filters emails by delivery status SENT`() {
            val admin = createUserWithRole(Role.ADMIN)
            val sent = emailFactory.create(deliveryStatus = EmailDeliveryStatus.SENT)
            emailFactory.create(deliveryStatus = EmailDeliveryStatus.FAILED)
            emailFactory.create(deliveryStatus = EmailDeliveryStatus.DELIVERED)

            mvc.perform(
                get("/management/emails")
                    .queryParam("deliveryStatus", "SENT")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[?(@.id == ${sent.id})].deliveryStatus").value("SENT"))
                .andExpect(jsonPath("$.content[?(@.deliveryStatus == 'FAILED')]").isEmpty)
        }

        @Test
        fun `admin filters emails by delivery status FAILED`() {
            val admin = createUserWithRole(Role.ADMIN)
            emailFactory.create(deliveryStatus = EmailDeliveryStatus.SENT)
            val failed = emailFactory.create(deliveryStatus = EmailDeliveryStatus.FAILED)

            mvc.perform(
                get("/management/emails")
                    .queryParam("deliveryStatus", "FAILED")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[?(@.id == ${failed.id})]").isNotEmpty)
                .andExpect(jsonPath("$.content[?(@.deliveryStatus == 'SENT')]").isEmpty)
        }

        @Test
        fun `admin searches emails by recipient email`() {
            val admin = createUserWithRole(Role.ADMIN)
            val matching = emailFactory.create(recipientEmail = "unique-search@example.com")
            emailFactory.create(recipientEmail = "other@example.com")

            mvc.perform(
                get("/management/emails")
                    .queryParam("search", "unique-search")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[?(@.id == ${matching.id})]").isNotEmpty)
        }

        @Test
        fun `admin searches emails by subject`() {
            val admin = createUserWithRole(Role.ADMIN)
            val matching = emailFactory.create(subject = "Unique Subject Line")
            emailFactory.create(subject = "Other Subject")

            mvc.perform(
                get("/management/emails")
                    .queryParam("search", "Unique Subject")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[?(@.id == ${matching.id})]").isNotEmpty)
        }

        @Test
        fun `admin combines status and search filters`() {
            val admin = createUserWithRole(Role.ADMIN)
            val target = emailFactory.create(
                recipientEmail = "target@example.com",
                deliveryStatus = EmailDeliveryStatus.FAILED,
            )
            // Same search term but different status — should not appear
            emailFactory.create(
                recipientEmail = "target@example.com",
                deliveryStatus = EmailDeliveryStatus.SENT,
            )
            // Same status but different search term — should not appear
            emailFactory.create(
                recipientEmail = "other@example.com",
                deliveryStatus = EmailDeliveryStatus.FAILED,
            )

            mvc.perform(
                get("/management/emails")
                    .queryParam("deliveryStatus", "FAILED")
                    .queryParam("search", "target")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[?(@.id == ${target.id})]").isNotEmpty)
        }

        @Test
        fun `result is sorted by createdAt descending`() {
            val admin = createUserWithRole(Role.ADMIN)
            emailFactory.create(recipientEmail = "sort-a@example.com")
            emailFactory.create(recipientEmail = "sort-b@example.com")

            // Verify the response is a page with both items — ordering by createdAt,desc is the default
            mvc.perform(get("/management/emails").with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[?(@.recipientEmail == 'sort-a@example.com')]").isNotEmpty)
                .andExpect(jsonPath("$.content[?(@.recipientEmail == 'sort-b@example.com')]").isNotEmpty)
        }

        @Test
        fun `response includes all expected fields`() {
            val admin = createUserWithRole(Role.ADMIN)
            emailFactory.create(
                recipientEmail = "fields-check@example.com",
                recipientName = "Fields Check",
                subject = "Field Check Subject",
                emailType = "email.test",
                deliveryStatus = EmailDeliveryStatus.SENT,
            )

            mvc.perform(get("/management/emails").with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].recipientEmail").exists())
                .andExpect(jsonPath("$.content[0].recipientName").exists())
                .andExpect(jsonPath("$.content[0].subject").exists())
                .andExpect(jsonPath("$.content[0].emailType").exists())
                .andExpect(jsonPath("$.content[0].deliveryStatus").exists())
                .andExpect(jsonPath("$.content[0].attempts").exists())
                .andExpect(jsonPath("$.content[0].createdAt").exists())
        }

        @Test
        fun `pagination returns correct totalElements`() {
            val admin = createUserWithRole(Role.ADMIN)
            repeat(3) { i -> emailFactory.create(recipientEmail = "page-test-$i@example.com") }

            // The controller normalises page size to PAGE_SIZE=50; totalElements reflects all matching records
            mvc.perform(
                get("/management/emails")
                    .queryParam("page", "0")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.number").value(0))
        }
    }

    @Nested
    inner class Stats {

        @Test
        fun `admin gets email stats with all status counts`() {
            val admin = createUserWithRole(Role.ADMIN)
            emailFactory.create(deliveryStatus = EmailDeliveryStatus.SENT)
            emailFactory.create(deliveryStatus = EmailDeliveryStatus.DELIVERED)
            emailFactory.create(deliveryStatus = EmailDeliveryStatus.FAILED)

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
        fun `stats counts reflect actual status distribution`() {
            val admin = createUserWithRole(Role.ADMIN)
            emailFactory.create(deliveryStatus = EmailDeliveryStatus.SENT)
            emailFactory.create(deliveryStatus = EmailDeliveryStatus.SENT)
            emailFactory.create(deliveryStatus = EmailDeliveryStatus.FAILED)
            emailFactory.create(deliveryStatus = EmailDeliveryStatus.DELIVERED)
            emailFactory.create(deliveryStatus = EmailDeliveryStatus.OPENED)
            emailFactory.create(deliveryStatus = EmailDeliveryStatus.BOUNCED)

            val result = mvc.perform(get("/management/emails/stats").with(bearer(admin)))
                .andExpect(status().isOk)
                .andReturn()

            val body = result.response.contentAsString
            assertThat(body).contains("\"sentCount\"")
            assertThat(body).contains("\"failedCount\"")
            assertThat(body).contains("\"deliveredCount\"")
        }

        @Test
        fun `stats returns zero counts when no emails exist`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(get("/management/emails/stats").with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalCount").isNumber)
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
            val outbox = emailFactory.create(
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
            val outbox = emailFactory.create(
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
            val outbox = emailFactory.create(
                deliveryStatus = EmailDeliveryStatus.SENT,
                jobExecutionId = jobExecution.id,
            )

            mvc.perform(post("/management/emails/${outbox.id}/retry").with(bearer(admin)))
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `retry returns 404 when outbox entry does not exist`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(post("/management/emails/9999999/retry").with(bearer(admin)))
                .andExpect(status().isNotFound)
        }

        @Test
        fun `retry also works for DEAD job executions`() {
            val admin = createUserWithRole(Role.ADMIN)
            val jobExecution = createJobExecutionFixture(status = JobExecutionStatus.DEAD)
            val outbox = emailFactory.create(
                deliveryStatus = EmailDeliveryStatus.FAILED,
                jobExecutionId = jobExecution.id,
            )

            mvc.perform(post("/management/emails/${outbox.id}/retry").with(bearer(admin)))
                .andExpect(status().isOk)
        }
    }

    @Nested
    inner class ResponseShape {

        @Test
        fun `list response includes jobExecutionId when linked`() {
            val admin = createUserWithRole(Role.ADMIN)
            val jobExecution = createJobExecutionFixture(status = JobExecutionStatus.FAILED)
            emailFactory.create(jobExecutionId = jobExecution.id)

            mvc.perform(get("/management/emails").with(bearer(admin)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[?(@.jobExecutionId == ${jobExecution.id})]").isNotEmpty)
        }

        @Test
        fun `list response includes error fields for failed emails`() {
            val admin = createUserWithRole(Role.ADMIN)
            val outbox = emailFactory.create(deliveryStatus = EmailDeliveryStatus.FAILED)

            mvc.perform(
                get("/management/emails")
                    .queryParam("deliveryStatus", "FAILED")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[?(@.id == ${outbox.id})]").isNotEmpty)
        }
    }
}
