package net.blueshell.api.declaration.web

import net.blueshell.api.contribution.domain.ContributionJobs
import net.blueshell.api.contribution.domain.WipeBankDetailsJob
import net.blueshell.api.declaration.domain.DeclarationJobs
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneOffset
import javax.imageio.ImageIO

@SpringBootTest
class DeclarationReviewIT : UserTestSupport() {
    private val json = JsonMapper.builder().build()

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var wipe: WipeBankDetailsJob

    @AfterEach
    fun resetClock() = clock.reset()

    private fun idOf(answer: String) = json.readTree(answer)["id"].asLong()

    /** A declaration [by] wrote with one PDF receipt, submitted when [submitted]. */
    private fun declared(
        by: User,
        submitted: Boolean = true,
        file: MockMultipartFile = MockMultipartFile("file", "shop.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-1.7\n%stub".toByteArray()),
    ): Long {
        val receipt =
            idOf(
                mvc
                    .perform(multipart("/declarations/receipts").file(file).with(signedIn(by)).with(csrfToken()))
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

    @Test
    fun `an approved declaration is paid to the account on file, keeps its preview, and the submitter is emailed`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val id = declared(member)

        act(board, id, "mark-paid").andExpect(status().isConflict).andExpect(jsonPath("$.code").value("DeclarationStateChanged"))
        act(board, id, "approve").andExpect(status().isOk)
        mvc
            .perform(get("/declarations/$id/bank-account").with(signedIn(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.bankAccount.accountHolder").value("Ann Vos"))
            .andExpect(jsonPath("$.bankAccount.ibanCountry").value("NL"))
            .andExpect(jsonPath("$.bankAccount.ibanLastTwo").value("00"))
        // The full IBAN opens only through a reveal, which is logged; a member without a mandate is revealed too.
        mvc
            .perform(post("/users/${member.id}/mandate/reveal").with(signedIn(board)).with(csrfToken()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.iban").value("NL91ABNA0417164300"))

        act(board, id, "mark-paid")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("PAID"))
            .andExpect(jsonPath("$.steps[3].by").value(board.fullName))
            .andExpect(jsonPath("$.steps[3].paidToCountry").value("NL"))
            .andExpect(jsonPath("$.steps[3].paidToLastTwo").value("00"))
        assertThat(findJobsByType(DeclarationJobs.DeclarationPaid.type).map { it.payload })
            .anySatisfy { assertThat(it).contains("\"declarationId\":$id", "\"paidTo\":\"NL00\"") }
        act(board, id, "mark-paid").andExpect(status().isConflict)
        act(board, id, "reject", "Too late").andExpect(status().isConflict)
        // A later account does not change where it went.
        putBankAccount(member, iban = "NL20INGB0001234567").andExpect(status().isOk)
        mvc.perform(get("/declarations/$id").with(signedIn(board))).andExpect(jsonPath("$.steps[3].paidToLastTwo").value("00"))
    }

    @Test
    fun `nobody below board reads the account to pay to or marks a declaration paid, and one without an account on file is not paid`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val id = declared(member)
        act(board, id, "approve").andExpect(status().isOk)

        mvc.perform(get("/declarations/$id/bank-account").with(signedIn(member))).andExpect(status().isForbidden)
        act(member, id, "mark-paid").andExpect(status().isForbidden)
        mvc
            .perform(delete("/users/me/bank-account").with(signedIn(member, steppedUp = true)).with(csrfToken()))
            .andExpect(status().is2xxSuccessful)

        mvc.perform(get("/declarations/$id/bank-account").with(signedIn(board))).andExpect(jsonPath("$.bankAccount").doesNotExist())
        act(board, id, "mark-paid").andExpect(status().isConflict).andExpect(jsonPath("$.code").value("SubmitterHasNoBankAccount"))
    }

    @Test
    fun `a declaration paid to an account is a use of it, so the 13-month wipe counts from the payout`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val id = declared(member)
        act(board, id, "approve").andExpect(status().isOk)
        val paidOn = LocalDate.now().plusMonths(6)
        clock.set(paidOn.atTime(10, 0).toInstant(ZoneOffset.UTC))
        act(board, id, "mark-paid").andExpect(status().isOk)

        fun wipedOn(day: LocalDate): Boolean {
            clock.set(day.atTime(4, 30).toInstant(ZoneOffset.UTC))
            wipe.handle(mapper.writeValueAsString(ContributionJobs.WipeBankDetailsPayload()), null, false)
            return jdbc.queryForObject("SELECT account_iban FROM payment_details WHERE user_id = ?", String::class.java, member.id) == null
        }
        assertThat(wipedOn(paidOn.plusMonths(13).minusDays(1))).isFalse()
        assertThat(wipedOn(paidOn.plusMonths(13))).isTrue()
    }

    @Test
    fun `the board downloads declarations as one PDF, each followed by its receipts, and nobody else does`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val first = declared(member)
        val photo = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB), "png", it) }
        val second = declared(member, file = MockMultipartFile("file", "bill.png", MediaType.IMAGE_PNG_VALUE, photo.toByteArray()))

        val answer =
            mvc
                .perform(get("/declarations/pdf").param("ids", "$first,$second").with(signedIn(board)))
                .andExpect(status().isOk)
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn()
                .response
        assertThat(answer.getHeader("Content-Disposition")).contains("declarations.pdf")
        Loader.loadPDF(answer.contentAsByteArray).use { pdf ->
            val text = PDFTextStripper().getText(pdf)
            assertThat(text).contains("Declaration form Blueshell E-Sports Vereniging Enschede", member.fullName, "NL•• … ••00")
            assertThat(text).doesNotContain("NL91ABNA0417164300", "0417")
            // Each form is followed by its one receipt: a stub PDF that cannot be read, and a photo kept as WebP.
            assertThat(pdf.numberOfPages).isEqualTo(4)
            assertThat(text).contains("This receipt is not in this download.")
            assertThat(pdf.getPage(3).resources.xObjectNames).isNotEmpty()
        }

        mvc.perform(get("/declarations/pdf").param("ids", "$first").with(signedIn(member))).andExpect(status().isForbidden)
        mvc.perform(get("/declarations/pdf").param("ids", "999999").with(signedIn(board))).andExpect(status().isNotFound)
        mvc
            .perform(get("/declarations/pdf").param("ids", "").with(signedIn(board)))
            .andExpect(status().isBadRequest)
    }
}
