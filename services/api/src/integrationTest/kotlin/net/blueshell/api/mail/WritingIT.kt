package net.blueshell.api.mail

import net.blueshell.api.email.persistence.EmailRepository
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class WritingIT : UserTestSupport() {
    @Autowired
    private lateinit var emails: EmailRepository

    @Test
    fun `the board writes to people, each copy is in Sent as queued with who wrote it, and members cannot`() {
        val board = createUserWithRole(Role.BOARD)
        val ann = createUserWithRole(Role.MEMBER)
        val bea = createUserWithRole(Role.MEMBER)
        val to = """[{"kind":"PERSON","id":"${ann.id}"},{"kind":"PERSON","id":"${bea.id}"},{"kind":"PERSON","id":"${ann.id}"}]"""

        mvc
            .perform(post("/mail/reach").with(signedIn(board)).contentType(MediaType.APPLICATION_JSON).content("""{"to":$to}"""))
            .andExpect(jsonPath("$.recipients").value(2))
            .andExpect(jsonPath("$.withoutEmail").value(0))
        mvc
            .perform(
                post("/mail/send")
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"to":$to,"subject":"LAN night","message":"**Hi** 🎮","replyTo":"board@example.com"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.sent").value(2))

        val written = emails.findAll().filter { it.subject == "LAN night" }
        assertThat(written.map { it.recipientEmail }).containsExactlyInAnyOrder(ann.email, bea.email)
        assertThat(written).allMatch { it.deliveryStatus == EmailDeliveryStatus.QUEUED && it.initiatedByUserId == board.id }
        assertThat(written.first().bodyMarkdown).contains("1f3ae.png")

        mvc.perform(get("/mail/audiences").with(signedIn(board))).andExpect(status().isOk)
        mvc.perform(get("/mail/reply-to").with(signedIn(board))).andExpect(jsonPath("$[1]").value(board.email))
        mvc
            .perform(
                post(
                    "/mail/send",
                ).with(signedIn(ann)).contentType(MediaType.APPLICATION_JSON).content("""{"to":$to,"subject":"x","message":"y"}"""),
            ).andExpect(status().isForbidden)
        mvc
            .perform(
                post(
                    "/mail/test",
                ).with(signedIn(board)).contentType(MediaType.APPLICATION_JSON).content("""{"subject":"","message":"y"}"""),
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("SubjectMissing"))
    }
}
