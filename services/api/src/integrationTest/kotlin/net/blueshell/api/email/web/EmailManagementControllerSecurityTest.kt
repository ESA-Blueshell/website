package net.blueshell.api.email.web

import net.blueshell.api.shared.enums.EmailDeliveryStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Security tests for EmailManagementController.
 *
 * | Endpoint                              | ADMIN | BOARD | MEMBER | Unauth |
 * |---------------------------------------|-------|-------|--------|--------|
 * | GET /management/emails                | 200   | 200   | 403    | 401    |
 * | GET /management/emails/stats          | 200   | 200   | 403    | 401    |
 * | GET /management/emails/{id}/preview   | 200   | 200   | 403    | 401    |
 * | POST /management/emails/{id}/retry    | 200   | 200   | 403    | 401    |
 */
@SpringBootTest
class EmailManagementControllerSecurityTest : UserTestSupport() {

    // GET /management/emails
    @Test fun `admin can list emails`() {
        val admin = createUserWithRole(Role.ADMIN)
        mvc.perform(get("/management/emails").with(bearer(admin))).andExpect(status().isOk)
    }

    @Test fun `board can list emails`() {
        val board = createUserWithRole(Role.BOARD)
        mvc.perform(get("/management/emails").with(bearer(board))).andExpect(status().isOk)
    }

    @Test fun `member cannot list emails`() {
        val member = createUserWithRole(Role.MEMBER)
        mvc.perform(get("/management/emails").with(bearer(member))).andExpect(status().isForbidden)
    }

    @Test fun `unauthenticated cannot list emails`() {
        mvc.perform(get("/management/emails")).andExpect(status().isUnauthorized)
    }

    // GET /management/emails/stats
    @Test fun `admin can get email stats`() {
        val admin = createUserWithRole(Role.ADMIN)
        mvc.perform(get("/management/emails/stats").with(bearer(admin))).andExpect(status().isOk)
    }

    @Test fun `board can get email stats`() {
        val board = createUserWithRole(Role.BOARD)
        mvc.perform(get("/management/emails/stats").with(bearer(board))).andExpect(status().isOk)
    }

    @Test fun `member cannot get email stats`() {
        val member = createUserWithRole(Role.MEMBER)
        mvc.perform(get("/management/emails/stats").with(bearer(member))).andExpect(status().isForbidden)
    }

    @Test fun `unauthenticated cannot get email stats`() {
        mvc.perform(get("/management/emails/stats")).andExpect(status().isUnauthorized)
    }

    // GET /management/emails/{id}/preview
    //
    // Each row is created with a body, so an allowed request is a 200 rather than the 404 a
    // row sent before bodies were stored would give. A 404 would pass a status check without
    // saying whether the reader was let in.
    @Test fun `admin can preview a sent email`() {
        val admin = createUserWithRole(Role.ADMIN)
        val outbox = emailFactory.create(bodyMarkdown = "Dear member, your contribution is due.")
        mvc.perform(get("/management/emails/${outbox.id}/preview").with(bearer(admin)))
            .andExpect(status().isOk)
    }

    @Test fun `board can preview a sent email`() {
        val board = createUserWithRole(Role.BOARD)
        val outbox = emailFactory.create(bodyMarkdown = "Dear member, your contribution is due.")
        // Reading what an email said is gated with reading the outbox it is listed in.
        mvc.perform(get("/management/emails/${outbox.id}/preview").with(bearer(board)))
            .andExpect(status().isOk)
    }

    @Test fun `member cannot preview a sent email`() {
        val member = createUserWithRole(Role.MEMBER)
        val outbox = emailFactory.create(bodyMarkdown = "Dear member, your contribution is due.")
        // The body carries somebody else's name and whatever the email told them.
        mvc.perform(get("/management/emails/${outbox.id}/preview").with(bearer(member)))
            .andExpect(status().isForbidden)
    }

    @Test fun `unauthenticated cannot preview a sent email`() {
        val outbox = emailFactory.create(bodyMarkdown = "Dear member, your contribution is due.")
        mvc.perform(get("/management/emails/${outbox.id}/preview")).andExpect(status().isUnauthorized)
    }

    // POST /management/emails/{id}/retry
    @Test fun `admin can retry email`() {
        val admin = createUserWithRole(Role.ADMIN)
        val outbox = emailFactory.create(
            deliveryStatus = EmailDeliveryStatus.FAILED,
            jobExecutionId = null,
        )
        // Exactly 400, not any 4xx: a 403 would otherwise pass and the test would say nothing
        // about whether the request was allowed. This row has no job to run again.
        mvc.perform(post("/management/emails/${outbox.id}/retry").with(bearer(admin)))
            .andExpect(status().isBadRequest)
    }

    @Test fun `board can retry email`() {
        val board = createUserWithRole(Role.BOARD)
        val outbox = emailFactory.create(
            deliveryStatus = EmailDeliveryStatus.FAILED,
            jobExecutionId = null,
        )
        // 400 rather than 403: the request was allowed, and the business rule rejected it
        // because this row has no job to run again.
        mvc.perform(post("/management/emails/${outbox.id}/retry").with(bearer(board)))
            .andExpect(status().isBadRequest)
    }

    @Test fun `member cannot retry email`() {
        val member = createUserWithRole(Role.MEMBER)
        val outbox = emailFactory.create(deliveryStatus = EmailDeliveryStatus.FAILED)
        mvc.perform(post("/management/emails/${outbox.id}/retry").with(bearer(member)))
            .andExpect(status().isForbidden)
    }

    @Test fun `unauthenticated cannot retry email`() {
        val outbox = emailFactory.create(deliveryStatus = EmailDeliveryStatus.FAILED)
        mvc.perform(post("/management/emails/${outbox.id}/retry")).andExpect(status().isUnauthorized)
    }
}
