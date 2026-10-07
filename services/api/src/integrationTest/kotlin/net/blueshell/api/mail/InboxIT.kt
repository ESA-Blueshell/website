package net.blueshell.api.mail

import net.blueshell.api.email.persistence.Email
import net.blueshell.api.email.persistence.EmailRepository
import net.blueshell.api.mail.domain.InboxIntake
import net.blueshell.api.mail.domain.InboxReplyJob
import net.blueshell.api.mail.domain.ParsedInboxMessage
import net.blueshell.api.mail.persistence.InboxReplyRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.testsupport.runJob
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@SpringBootTest
class InboxIT : UserTestSupport() {
    @Autowired
    private lateinit var intake: InboxIntake

    @Autowired
    private lateinit var emails: EmailRepository

    @Autowired
    private lateinit var inboxReplies: InboxReplyRepository

    @Autowired
    private lateinit var replyJob: InboxReplyJob

    private fun received(
        id: String,
        from: String,
        thread: List<String> = emptyList(),
        to: String = "board@esa-blueshell.nl",
        automatic: Boolean = false,
    ) = ParsedInboxMessage(
        id,
        thread.firstOrNull(),
        thread,
        from,
        null,
        to,
        "Re: Your contribution",
        "Paid",
        null,
        Instant.now(),
        automatic,
    )

    @Test
    fun `a reply is matched to the email it answers and its sender, other mail keeps its address, and only the board reads it`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val sentId = "<${UUID.randomUUID()}@blueshell>"
        val sent =
            emails.save(
                Email(
                    recipientEmail = member.email,
                    subject = "Your contribution",
                    emailType = "email.contribution-reminder",
                    messageId = sentId,
                ),
            )
        val stamp = UUID.randomUUID()

        intake.take(received("<reply-$stamp>", member.email, listOf(sentId)))
        assertThat(intake.take(received("<reply-$stamp>", member.email, listOf(sentId)))).isNull()
        intake.take(received("<partner-$stamp>", "info@sponsor-$stamp.example", to = "partners@esa-blueshell.nl"))
        intake.take(received("<away-$stamp>", member.email, automatic = true))

        mvc
            .perform(get("/mail/inbox").param("search", member.email).with(signedIn(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[?(@.automatic == false)].answers.emailId").value(sent.id!!.toInt()))
            .andExpect(jsonPath("$.content[?(@.automatic == false)].senderUserId").value(member.id!!.toInt()))
            .andExpect(jsonPath("$.content[?(@.automatic == false)].state").value("NEW"))
        mvc
            .perform(get("/mail/inbox").param("search", "sponsor-$stamp").with(signedIn(board)))
            .andExpect(jsonPath("$.content[0].toAddress").value("partners@esa-blueshell.nl"))
            .andExpect(jsonPath("$.content[0].senderUserId").doesNotExist())
        mvc.perform(get("/mail/inbox/counts").with(signedIn(board))).andExpect(status().isOk)
        mvc.perform(get("/mail/inbox").with(signedIn(member))).andExpect(status().isForbidden)
    }

    @Test
    fun `orders the inbox by who handled a message, and narrows it to one address's mailbox`() {
        val board = createUserWithRole(Role.BOARD)
        val stamp = UUID.randomUUID()
        val handled = requireNotNull(intake.take(received("<handled-$stamp>", "a-$stamp@example.org")))
        intake.take(received("<waiting-$stamp>", "b-$stamp@example.org"))
        intake.take(received("<events-$stamp>", "c-$stamp@example.org"), "events-$stamp@b.nl")
        mvc.perform(post("/mail/inbox/{id}/handled", handled.id).with(signedIn(board))).andExpect(status().isOk)

        mvc
            .perform(
                get("/mail/inbox")
                    .param("search", stamp.toString())
                    .param("sort", "HANDLED_BY")
                    .param("descending", "true")
                    .with(signedIn(board)),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(3))
            .andExpect(jsonPath("$.content[0].handledBy").value(board.id!!.toInt()))
        mvc
            .perform(get("/mail/inbox").param("mailbox", "events-$stamp@b.nl").with(signedIn(board)))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].mailbox").value("events-$stamp@b.nl"))
    }

    @Test
    fun `the board answers a message in its thread and the conversation shows it, or marks it handled, and members cannot`() {
        val board = createUserWithRole(Role.BOARD)
        val member = createUserWithRole(Role.MEMBER)
        val sentId = "<${UUID.randomUUID()}@blueshell>"
        val sent =
            emails.save(
                Email(
                    recipientEmail = member.email,
                    subject = "Your contribution",
                    emailType = "email.contribution-reminder",
                    messageId = sentId,
                ),
            )
        val stamp = UUID.randomUUID()
        val message = intake.take(received("<reply-$stamp>", member.email, listOf(sentId)))!!
        val other = intake.take(received("<other-$stamp>", member.email))!!
        resetEmailClient()

        mvc
            .perform(
                post("/mail/inbox/${message.id}/reply")
                    .with(signedIn(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"message":"Thanks, **received**","replyTo":"board@example.com"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.message.state").value("REPLIED"))
            .andExpect(jsonPath("$.message.handledBy").value(board.id!!.toInt()))
            .andExpect(jsonPath("$.items[0].kind").value("SENT"))
            .andExpect(jsonPath("$.items[0].emailId").value(sent.id!!.toInt()))
            .andExpect(jsonPath("$.items[2].kind").value("REPLY"))
            .andExpect(jsonPath("$.items[2].body").value("Thanks, **received**"))
            .andExpect(jsonPath("$.earlier[0].inboxMessageId").value(other.id!!.toInt()))
        val inSent = emails.findAll().single { it.recipientEmail == member.email && it.subject == "Re: Your contribution" }
        assertThat(inSent.initiatedByUserId).isEqualTo(board.id)
        val reply = inboxReplies.findByInboxMessageIdInOrderByWrittenAtAsc(listOf(message.id!!)).single()
        replyJob.runJob("""{"inboxReplyId":${reply.id}}""")
        val out = emailTransportClient.sentEmails.single()
        assertThat(out.toEmail).isEqualTo(member.email)
        assertThat(out.subject).isEqualTo("Re: Your contribution")
        assertThat(out.replyToAddress).isEqualTo("board@example.com")
        assertThat(out.threadHeaders).containsEntry("In-Reply-To", "<reply-$stamp>").containsEntry("References", "$sentId <reply-$stamp>")

        mvc
            .perform(post("/mail/inbox/${other.id}/handled").with(signedIn(board)))
            .andExpect(jsonPath("$.message.state").value("HANDLED"))
            .andExpect(jsonPath("$.items.length()").value(1))
        mvc.perform(get("/mail/inbox/0").with(signedIn(board))).andExpect(status().isNotFound)
        mvc.perform(post("/mail/inbox/${other.id}/handled").with(signedIn(member))).andExpect(status().isForbidden)
    }
}
