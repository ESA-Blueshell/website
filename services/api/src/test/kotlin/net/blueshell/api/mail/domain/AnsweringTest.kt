package net.blueshell.api.mail.domain

import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.email.api.SentEmailRef
import net.blueshell.api.email.api.SentEmails
import net.blueshell.api.email.api.SiteMarkdownEmails
import net.blueshell.api.mail.persistence.InboxMessage
import net.blueshell.api.mail.persistence.InboxMessageRepository
import net.blueshell.api.mail.persistence.InboxReply
import net.blueshell.api.mail.persistence.InboxReplyRepository
import net.blueshell.api.mail.persistence.InboxState
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.job.NonRetryableJobException
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class AnsweringTest {
    private val now = Instant.parse("2026-10-01T10:00:00Z")
    private val messages: InboxMessageRepository = mock()
    private val replies: InboxReplyRepository = mock()
    private val jobs: JobQueue = mock()
    private val answering = Answering(messages, replies, jobs, Clock.fixed(now, ZoneOffset.UTC))

    private fun received(
        id: Long,
        messageId: String = "<r$id>",
        subject: String = "Re: Your contribution",
        thread: String? = "<sent-9>",
        at: Instant = Instant.EPOCH,
    ) = InboxMessage(messageId, thread, thread, "lars@example.com", "Lars", "board@b.nl", subject, "Paid", null, at, false, 9, 5)
        .apply { this.id = id }

    @Test
    fun `a reply is kept on the message, queued as an email, and marks it replied by who and when`() {
        val message = received(1)
        whenever(messages.findById(1)).thenReturn(Optional.of(message))
        whenever(messages.save(any<InboxMessage>())).thenAnswer { it.arguments[0] }
        whenever(replies.save(any<InboxReply>())).thenAnswer { (it.arguments[0] as InboxReply).also { reply -> reply.id = 4 } }

        val answered = answering.reply(1, "Thanks!", " ", 6)

        verify(jobs).runAsync(eq(MailJobs.InboxReply), eq(MailJobs.InboxReplyPayload(4)), eq(JobTrigger.SITE_ACTION), eq(null))
        assertThat(listOf(answered.state, answered.handledBy, answered.handledAt)).containsExactly(InboxState.REPLIED, 6L, now)
        assertThat(MailJobs.InboxReply.dedupKey(MailJobs.InboxReplyPayload(4))).isNull()
    }

    @Test
    fun `a message is marked handled without a reply, and an empty reply or a missing message is refused`() {
        whenever(messages.findById(1)).thenReturn(Optional.of(received(1)))
        whenever(messages.findById(2)).thenReturn(Optional.empty())
        whenever(messages.save(any<InboxMessage>())).thenAnswer { it.arguments[0] }

        assertThat(answering.markHandled(1, 6).state).isEqualTo(InboxState.HANDLED)
        assertThatThrownBy { answering.reply(1, " ", null, 6) }.isInstanceOf(MessageMissing::class.java)
        assertThatThrownBy { answering.markHandled(2, 6) }.isInstanceOf(InboxMessageNotFound::class.java)
        verify(replies, never()).save(any<InboxReply>())
    }

    @Test
    fun `the reply goes to the sender as Re of their subject, threaded after everything the message names`() {
        val siteMarkdown: SiteMarkdownEmails = mock()
        val job = InboxReplyJob(JsonMapper(), mock<EmailSenderService>(), replies, messages, siteMarkdown)
        whenever(replies.findById(4)).thenReturn(Optional.of(InboxReply(1, "**Thanks**", "board@b.nl", 6, now)))
        whenever(replies.findById(5)).thenReturn(Optional.of(InboxReply(2, "Hi", null, 6, now)))
        whenever(replies.findById(6)).thenReturn(Optional.empty())
        whenever(messages.findById(1)).thenReturn(Optional.of(received(1, thread = "<b> <a>")))
        whenever(messages.findById(2)).thenReturn(Optional.of(received(2, subject = " ", thread = null)))
        whenever(siteMarkdown.forEmail(any())).thenAnswer { "<p>${it.arguments[0]}</p>" }

        assertThat(job.composeQueued("""{"inboxReplyId":4}"""))
            .isEqualTo(
                EmailContent(
                    "lars@example.com",
                    "Lars",
                    "Re: Your contribution",
                    "<p>**Thanks**</p>",
                    replyToOverride = "board@b.nl",
                    inReplyTo = "<r1>",
                    references = listOf("<a>", "<b>", "<r1>"),
                ),
            )
        val bare = job.composeQueued("""{"inboxReplyId":5}""")!!
        assertThat(listOf(bare.subject, bare.threadHeaders["References"])).containsExactly("Re: Your message", "<r2>")
        assertThatThrownBy { job.composeQueued("""{"inboxReplyId":6}""") }.isInstanceOf(NonRetryableJobException::class.java)
        assertThat(InboxReply::class.java.getDeclaredConstructor().newInstance()).isNotNull
    }

    @Test
    fun `a conversation runs from the email it answers through every answer and reply, with other mail kept apart`() {
        val sent: SentEmails = mock()
        val users: UserService = mock()
        val first = received(1, at = Instant.parse("2026-09-01T10:00:00Z"))
        val second = received(2, at = Instant.parse("2026-09-02T10:00:00Z"))
        val other =
            received(3, thread = null, at = Instant.parse("2026-08-01T10:00:00Z")).let {
                InboxMessage(
                    it.messageId,
                    null,
                    null,
                    it.fromAddress,
                    null,
                    null,
                    "Hello",
                    null,
                    "<p>Hi</p>",
                    it.receivedAt,
                    false,
                    null,
                    5,
                ).apply { id = 3 }
            }
        val reminder =
            SentEmailRef(9, "email.contribution-reminder", "lars@example.com", "Your contribution", Instant.parse("2026-08-30T10:00:00Z"))
        val welcome = SentEmailRef(8, "email.welcome", "lars@example.com", "Welcome", Instant.parse("2026-07-01T10:00:00Z"))
        val reply = InboxReply(2, "Thanks", null, 6, Instant.parse("2026-09-03T10:00:00Z"))
        whenever(messages.findById(2)).thenReturn(Optional.of(second))
        whenever(messages.findById(7)).thenReturn(Optional.empty())
        whenever(messages.findTop20ByFromAddressOrderByReceivedAtDesc("lars@example.com")).thenReturn(listOf(second, first, other))
        whenever(replies.findByInboxMessageIdInOrderByWrittenAtAsc(listOf(2L, 1L))).thenReturn(listOf(reply))
        whenever(sent.byIds(listOf(9L))).thenReturn(mapOf(9L to reminder))
        whenever(sent.toAddress("lars@example.com")).thenReturn(listOf(reminder, welcome))
        whenever(users.findAllByIds(setOf(6L, 5L))).thenReturn(
            listOf(
                Entities.user(id = 5, firstName = "Lars", lastName = "Mulder"),
                Entities.user(id = 6, firstName = "Alice", lastName = "Board"),
            ),
        )
        val inbox = Inbox(messages, replies, sent, users)

        val conversation = inbox.conversation(2)

        assertThat(conversation.message.id).isEqualTo(2)
        assertThat(conversation.items.map { it.kind to (it.emailId ?: it.inboxMessageId) }).containsExactly(
            ConversationKind.SENT to 9L,
            ConversationKind.RECEIVED to 1L,
            ConversationKind.RECEIVED to 2L,
            ConversationKind.REPLY to 2L,
        )
        assertThat(conversation.items.last().writtenByName).isEqualTo("Alice Board")
        assertThat(conversation.earlier).containsExactly(
            EarlierMail(ConversationKind.RECEIVED, other.receivedAt, "Hello", null, 3),
            EarlierMail(ConversationKind.SENT, welcome.sentAt, "Welcome", 8, null),
        )
        assertThatThrownBy { inbox.conversation(7) }.isInstanceOf(InboxMessageNotFound::class.java)
    }

    @Test
    fun `a message that answers nothing the site sent is a conversation of its own`() {
        val sent: SentEmails = mock()
        val users: UserService = mock()
        val alone =
            received(3, thread = null).let {
                InboxMessage(
                    it.messageId,
                    null,
                    null,
                    it.fromAddress,
                    null,
                    null,
                    "Hello",
                    null,
                    "<p>Hi</p>",
                    it.receivedAt,
                    false,
                    null,
                    null,
                ).apply { id = 3 }
            }
        whenever(messages.findById(3)).thenReturn(Optional.of(alone))
        whenever(messages.findTop20ByFromAddressOrderByReceivedAtDesc("lars@example.com")).thenReturn(listOf(alone))
        whenever(replies.findByInboxMessageIdInOrderByWrittenAtAsc(listOf(3L))).thenReturn(emptyList())
        whenever(sent.byIds(emptyList())).thenReturn(emptyMap())
        whenever(sent.toAddress("lars@example.com")).thenReturn(emptyList())
        whenever(users.findAllByIds(emptySet())).thenReturn(emptyList())

        val conversation = Inbox(messages, replies, sent, users).conversation(3)

        assertThat(conversation.items.single().body).isEqualTo("Hi")
        assertThat(conversation.earlier).isEmpty()
        assertThat(
            plainOf("<style>p{}</style><div>Hi  <b>Lars</b>,<br>Paid?</div><p></p><p></p><p>Bye</p>"),
        ).isEqualTo("Hi  Lars,\nPaid?\n\nBye")
    }
}
