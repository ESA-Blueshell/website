package net.blueshell.api.declaration.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@SpringBootTest
class DeclarationIT : UserTestSupport() {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private val json = JsonMapper.builder().build()

    private fun photo(
        width: Int,
        height: Int,
    ) = MockMultipartFile(
        "file",
        "bill.jpg",
        MediaType.IMAGE_JPEG_VALUE,
        ByteArrayOutputStream().also { ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg", it) }.toByteArray(),
    )

    private fun upload(
        by: User,
        file: MockMultipartFile,
    ): Long {
        val answer =
            mvc
                .perform(multipart("/declarations/receipts").file(file).with(signedIn(by)).with(csrfToken()))
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString
        return json.readTree(answer)["id"].asLong()
    }

    private fun draft(
        receipt: Long?,
        confirmed: Boolean = true,
        bookedTo: String = "null",
    ) = """
        {"expenses":[{"paidOn":"2026-01-10","description":"Pizza for the committee meeting","amountCents":1250,
          "bookedTo":$bookedTo,"receipts":[${receipt ?: ""}]}],"receipts":[],"confirmed":$confirmed}
        """.trimIndent()

    private fun write(
        by: User,
        body: String,
        id: Long? = null,
    ) = mvc.perform(
        (if (id == null) post("/users/me/declarations") else put("/users/me/declarations/$id"))
            .with(signedIn(by))
            .with(csrfToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body),
    )

    private fun act(
        by: User,
        id: Long,
        what: String,
    ) = mvc.perform(post("/users/me/declarations/$id/$what").with(signedIn(by)).with(csrfToken()))

    @Test
    fun `anybody with an account drafts, submits once payable and withdraws a declaration, each step kept`() {
        val guest = createUserWithRole(Role.GUEST)
        val committee = createCommitteeFixture(name = "Promo Committee")
        val receipt = upload(guest, photo(3000, 1000))
        assertThat(jdbc.queryForObject("SELECT width FROM files WHERE id = ?", Int::class.java, receipt)).isEqualTo(1600)
        assertThat(jdbc.queryForObject("SELECT media_type FROM files WHERE id = ?", String::class.java, receipt)).isEqualTo("image/webp")

        val started =
            write(guest, draft(receipt = null, confirmed = false, bookedTo = """{"kind":"COMMITTEE","id":${committee.id}}"""))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andExpect(jsonPath("$.expenses[0].bookedTo.name").value("Promo Committee"))
                .andReturn()
                .response.contentAsString
        val id = json.readTree(started)["id"].asLong()

        act(guest, id, "submit").andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("NoReceipt"))
        write(guest, draft(receipt, confirmed = false), id).andExpect(status().isOk)
        act(guest, id, "submit").andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("NotConfirmed"))
        write(guest, draft(receipt), id).andExpect(jsonPath("$.expenses[0].receipts[0].url").value("/declarations/receipts/$receipt"))
        act(guest, id, "submit").andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("NoBankAccount"))

        mvc
            .perform(
                put("/users/me/bank-account")
                    .with(signedIn(guest, steppedUp = true))
                    .with(csrfToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"iban":"NL91ABNA0417164300","accountHolder":"Ann Vos"}"""),
            ).andExpect(status().isOk)
        act(guest, id, "submit").andExpect(status().isOk).andExpect(jsonPath("$.state").value("SUBMITTED"))
        write(guest, draft(receipt), id).andExpect(status().isConflict).andExpect(jsonPath("$.code").value("DeclarationNotDraft"))

        act(guest, id, "withdraw")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.state").value("DRAFT"))
            .andExpect(jsonPath("$.confirmed").value(false))
            .andExpect(jsonPath("$.steps[*].state").value(org.hamcrest.Matchers.contains("DRAFT", "SUBMITTED", "DRAFT")))
            .andExpect(jsonPath("$.steps[2].by").value(guest.fullName))
        mvc
            .perform(get("/users/me/declarations").with(signedIn(guest)))
            .andExpect(jsonPath("$[0].id").value(id))
            .andExpect(header().string("X-Frame-Options", "DENY"))
    }

    @Test
    fun `a receipt opens for its submitter and, once submitted, the board, and goes with a deleted draft`() {
        val member = createUserWithRole(Role.MEMBER)
        val other = createUserWithRole(Role.MEMBER)
        val board = createUserWithRole(Role.BOARD)
        val pdf = upload(member, MockMultipartFile("file", "shop.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-1.7\n%stub".toByteArray()))
        val claimed = MockMultipartFile("file", "x.pdf", MediaType.APPLICATION_PDF_VALUE, photo(1, 1).bytes)
        mvc
            .perform(multipart("/declarations/receipts").file(claimed).with(signedIn(member)).with(csrfToken()))
            .andExpect(status().isBadRequest)
        val id = json.readTree(write(member, draft(pdf)).andReturn().response.contentAsString)["id"].asLong()

        mvc
            .perform(get("/declarations/receipts/$pdf").with(signedIn(member)))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
        mvc.perform(get("/declarations/receipts/$pdf").with(signedIn(other))).andExpect(status().isNotFound)
        mvc.perform(get("/declarations/receipts/$pdf").with(signedIn(board))).andExpect(status().isNotFound)
        mvc.perform(get("/users/me/declarations/$id").with(signedIn(other))).andExpect(status().isNotFound)
        write(other, draft(pdf)).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("UnknownReceipt"))

        mvc
            .perform(delete("/users/me/declarations/$id").with(signedIn(member)).with(csrfToken()))
            .andExpect(status().isNoContent)
        mvc.perform(get("/declarations/receipts/$pdf").with(signedIn(member))).andExpect(status().isNotFound)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM declarations WHERE id = ?", Int::class.java, id)).isZero()
    }
}
