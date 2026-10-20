package net.blueshell.api.declaration.web

import net.blueshell.api.declaration.domain.DeclarationJobs
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper

@SpringBootTest
class DeclarationReviewIT : UserTestSupport() {
    private val json = JsonMapper.builder().build()

    private fun idOf(answer: String) = json.readTree(answer)["id"].asLong()

    /** A declaration [by] wrote with one PDF receipt, submitted when [submitted]. */
    private fun declared(
        by: User,
        submitted: Boolean = true,
    ): Long {
        val pdf = MockMultipartFile("file", "shop.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-1.7\n%stub".toByteArray())
        val receipt =
            idOf(
                mvc
                    .perform(multipart("/declarations/receipts").file(pdf).with(signedIn(by)).with(csrfToken()))
                    .andReturn()
                    .response.contentAsString,
            )
        val body =
            """{"expenses":[{"paidOn":"2026-01-10","description":"Pizza for the committee meeting","amountCents":1250,""" +
                """"receipts":[$receipt]}],"receipts":[],"confirmed":true}"""
        val id =
            idOf(
                mvc
                    .perform(
                        post("/users/me/declarations")
                            .with(signedIn(by))
                            .with(csrfToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body),
                    ).andExpect(status().isCreated)
                    .andReturn()
                    .response.contentAsString,
            )
        if (submitted) {
            putBankAccount(by).andExpect(status().isOk)
            mvc.perform(post("/users/me/declarations/$id/submit").with(signedIn(by)).with(csrfToken())).andExpect(status().isOk)
        }
        return id
    }

    private fun act(
        by: User,
        id: Long,
        what: String,
        reason: String? = null,
    ) = mvc.perform(
        post("/declarations/$id/$what")
            .with(signedIn(by))
            .with(csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(reason?.let { json.writeValueAsString(mapOf("reason" to it)) } ?: "{}"),
    )

    @Test
    fun `the board lists what is past draft, and a draft stays its submitter's alone`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val sent = declared(member)
        val kept = declared(member, submitted = false)

        mvc
            .perform(get("/declarations").with(signedIn(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[*].id").value(hasItem(sent.toInt())))
            .andExpect(jsonPath("$[*].id").value(not(hasItem(kept.toInt()))))
        mvc
            .perform(get("/declarations/$sent").with(signedIn(board)))
            .andExpect(jsonPath("$.submitterId").value(member.id))
            .andExpect(jsonPath("$.submitter").value(member.fullName))
            .andExpect(jsonPath("$.expenses[0].receipts[0].mediaType").value("application/pdf"))
        mvc.perform(get("/declarations/$kept").with(signedIn(board))).andExpect(status().isNotFound)
    }

    @Test
    fun `nobody below board reads or moves a declaration, not even its submitter`() {
        val member = createUserWithRole(Role.MEMBER)
        val committee = createUserWithRole(Role.COMMITTEE)
        val id = declared(member)

        for (reader in listOf(member, committee)) {
            mvc.perform(get("/declarations").with(signedIn(reader))).andExpect(status().isForbidden)
            mvc.perform(get("/declarations/$id").with(signedIn(reader))).andExpect(status().isForbidden)
            act(reader, id, "approve").andExpect(status().isForbidden)
        }
    }

    @Test
    fun `a board member approves, and may approve their own`() {
        val board = createUserWithRole(Role.BOARD)
        val id = declared(board)

        act(board, id, "approve")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("APPROVED"))
            .andExpect(jsonPath("$.steps[*].state").value(contains("DRAFT", "SUBMITTED", "APPROVED")))
            .andExpect(jsonPath("$.steps[2].by").value(board.fullName))
        act(board, id, "approve").andExpect(status().isConflict).andExpect(jsonPath("$.code").value("DeclarationStateChanged"))
        // Approved, the submitter can no longer withdraw it.
        mvc
            .perform(post("/users/me/declarations/$id/withdraw").with(signedIn(board)).with(csrfToken()))
            .andExpect(status().isConflict)
    }

    @Test
    fun `sending back makes it a draft again with the reason, and the submitter is emailed it`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val id = declared(member)

        act(board, id, "send-back").andExpect(status().isBadRequest)
        act(board, id, "send-back", "  ").andExpect(status().isBadRequest)
        act(board, id, "send-back", "The receipt is cut off.")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("DRAFT"))
            .andExpect(jsonPath("$.confirmed").value(false))
            .andExpect(jsonPath("$.steps[2].reason").value("The receipt is cut off."))
        assertThat(findJobsByType(DeclarationJobs.DeclarationReturned.type).map { it.payload })
            .anySatisfy { assertThat(it).contains("\"declarationId\":$id", "SENT_BACK", "The receipt is cut off.") }

        // A draft sent back is the submitter's alone to change and submit again.
        mvc.perform(post("/users/me/declarations/$id/submit").with(signedIn(member)).with(csrfToken())).andExpect(status().isBadRequest)
        mvc.perform(get("/declarations/$id").with(signedIn(board))).andExpect(status().isNotFound)
        act(board, id, "approve").andExpect(status().isNotFound)
    }

    @Test
    fun `rejecting is final, from submitted or approved, and the submitter is emailed why`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val id = declared(member)
        act(board, id, "approve").andExpect(status().isOk)

        act(board, id, "reject", "This was not for the association.")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("REJECTED"))
            .andExpect(jsonPath("$.steps[3].reason").value("This was not for the association."))
        assertThat(findJobsByType(DeclarationJobs.DeclarationReturned.type).map { it.payload })
            .anySatisfy { assertThat(it).contains("\"declarationId\":$id", "REJECTED") }
        act(board, id, "send-back", "Again").andExpect(status().isConflict).andExpect(jsonPath("$.code").value("DeclarationStateChanged"))
        act(board, 999_999, "approve").andExpect(status().isNotFound)
    }
}
