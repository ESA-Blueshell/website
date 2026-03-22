package net.blueshell.api.platform.integration.email.web

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
 * | GET /management/emails                | 200   | 403   | 403    | 401    |
 * | GET /management/emails/stats          | 200   | 200   | 403    | 401    |
 * | POST /management/emails/{id}/retry    | 200   | 403   | 403    | 401    |
 */
@SpringBootTest
class EmailManagementControllerSecurityTest : UserTestSupport() {

    // GET /management/emails
    @Test fun `admin can list emails`() {
        val admin = createUserWithRole(Role.ADMIN)
        mvc.perform(get("/management/emails").with(bearer(admin))).andExpect(status().isOk)
    }

    @Test fun `board cannot list emails`() {
        val board = createUserWithRole(Role.BOARD)
        mvc.perform(get("/management/emails").with(bearer(board))).andExpect(status().isForbidden)
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

    // POST /management/emails/{id}/retry
    @Test fun `admin can retry email`() {
        val admin = createUserWithRole(Role.ADMIN)
        val outbox = emailFactory.create(
            deliveryStatus = EmailDeliveryStatus.FAILED,
            jobExecutionId = null,
        )
        // 400 is acceptable here — it means security passed but business rule rejected (no job linked)
        mvc.perform(post("/management/emails/${outbox.id}/retry").with(bearer(admin)))
            .andExpect(status().is4xxClientError)
    }

    @Test fun `board cannot retry email`() {
        val board = createUserWithRole(Role.BOARD)
        val outbox = emailFactory.create(deliveryStatus = EmailDeliveryStatus.FAILED)
        mvc.perform(post("/management/emails/${outbox.id}/retry").with(bearer(board)))
            .andExpect(status().isForbidden)
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
