package net.blueshell.api.mail.domain

import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.UIDFolder
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import net.blueshell.api.email.api.SentEmailRef
import net.blueshell.api.email.api.SentEmails
import net.blueshell.api.mail.persistence.InboxCursor
import net.blueshell.api.mail.persistence.InboxCursorRepository
import net.blueshell.api.mail.persistence.InboxMessage
import net.blueshell.api.mail.persistence.InboxMessageRepository
import net.blueshell.api.mail.persistence.InboxState
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.UserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.core.env.Environment
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.Properties

class InboxTest {
    private val messages: InboxMessageRepository = mock()
    private val sent: SentEmails = mock()
    private val users: UserService = mock()
    private val intake = InboxIntake(messages, sent, users)
    private val lars = Entities.user(id = 5, firstName = "Lars", lastName = "Mulder")
    private val reminder = SentEmailRef(9, "email.contribution-reminder", "lars@example.com", "Your contribution", Instant.EPOCH)

    private fun parsed(id: String) =
        ParsedInboxMessage(
            id,
            "<sent-9>",
            listOf("<sent-9>", "<sent-1>"),
            "lars@example.com",
            "Lars",
            "board@b.nl",
            "Re: x",
            "Hi",
            null,
            Instant.EPOCH,
            false,
        )

    @Test
    fun `keeps a message once, matched to the email it answers and the person who sent it`() {
        whenever(messages.existsByMessageId("<old>")).thenReturn(true)
        whenever(sent.answeredBy(listOf("<sent-9>", "<sent-1>"))).thenReturn(reminder)
        whenever(users.findAllByEmails(listOf("lars@example.com"))).thenReturn(listOf(lars))
        whenever(messages.save(any<InboxMessage>())).thenAnswer { it.arguments[0] }

        assertThat(intake.take(parsed("<old>"))).isNull()
        val kept = intake.take(parsed("<new>"))!!
        assertThat(kept.answersEmailId).isEqualTo(9)
        assertThat(kept.senderUserId).isEqualTo(5)
        assertThat(kept.threadIds).isEqualTo("<sent-9> <sent-1>")
        assertThat(kept.state).isEqualTo(InboxState.NEW)
    }

    @Test
    fun `reads the mailbox past its last UID without changing it, and again from the start when its UIDs are new`() {
        val session = Session.getInstance(Properties())
        val one =
            MimeMessage(session).apply {
                setFrom(InternetAddress("a@x.nl"))
                subject = "One"
                setText("1")
                saveChanges()
            }
        val two =
            MimeMessage(session).apply {
                setFrom(InternetAddress("b@x.nl"))
                subject = "Two"
                setText("2")
                saveChanges()
            }
        val folder: Folder = mock(extraInterfaces = arrayOf(UIDFolder::class))
        val uids = folder as UIDFolder
        whenever(uids.uidValidity).thenReturn(7)
        whenever(uids.getMessagesByUID(4, UIDFolder.MAXUID)).thenReturn(arrayOf<Message>(one, two))
        whenever(uids.getUID(one)).thenReturn(3)
        whenever(uids.getUID(two)).thenReturn(5)
        val cursors: InboxCursorRepository = mock()
        whenever(cursors.findById("INBOX")).thenReturn(Optional.of(InboxCursor("INBOX", 7, 3)))
        val taken = mock<InboxIntake>()
        val reading = MailboxReading(taken, cursors, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
        val poller = InboxPoller(reading, "", 993, "", "INBOX", true, mock<Environment>())

        poller.read(folder)

        verify(folder).open(Folder.READ_ONLY)
        val saved = argumentCaptor<InboxCursor>()
        verify(cursors).save(saved.capture())
        assertThat(saved.firstValue.lastUid).isEqualTo(5)
        verify(taken, times(1)).take(any(), eq(null))

        whenever(cursors.findById("INBOX")).thenReturn(Optional.of(InboxCursor("INBOX", 6, 99)))
        whenever(uids.getMessagesByUID(1, UIDFolder.MAXUID)).thenReturn(arrayOf())
        poller.read(folder)
        verify(uids).getMessagesByUID(1, UIDFolder.MAXUID)
        verify(folder, never()).close(true)

        // An address's own mailbox keeps its place apart, and its messages say whose mailbox they came from.
        whenever(cursors.findById("address:events@x.nl")).thenReturn(Optional.empty())
        whenever(uids.getMessagesByUID(1, UIDFolder.MAXUID)).thenReturn(arrayOf<Message>(two))
        reading.read(folder, AddressInboxPoller.cursorKeyOf("Events@x.nl"), "events@x.nl")
        verify(taken).take(any(), eq("events@x.nl"))
        verify(cursors, times(3)).save(saved.capture())
        assertThat(saved.lastValue.folder).isEqualTo("address:events@x.nl")
    }

    @Test
    fun `lists messages with what they answer and who sent and dealt with them, and counts the inbox`() {
        val reply =
            InboxMessage(
                "<r>",
                "<sent-9>",
                "<sent-9>",
                "lars@example.com",
                "Lars",
                "board@b.nl",
                "Re: x",
                "Hi",
                null,
                Instant.EPOCH,
                false,
                9,
                5,
                "events@x.nl",
            ).apply {
                id = 1
                state = InboxState.REPLIED
                handledBy = 6
                handledAt = Instant.EPOCH
            }
        val page = PageRequest.of(0, 50)
        whenever(messages.search("lars", "events@x.nl", page)).thenReturn(PageImpl(listOf(reply), page, 1))
        whenever(sent.byIds(listOf(9L))).thenReturn(mapOf(9L to reminder))
        whenever(users.findAllByIds(setOf(5L, 6L))).thenReturn(listOf(lars, Entities.user(id = 6, firstName = "Alice", lastName = "Board")))
        whenever(messages.countByStateAndAutomaticFalse(InboxState.NEW)).thenReturn(3)
        whenever(messages.countByStateAndAutomaticFalse(InboxState.REPLIED)).thenReturn(100)
        whenever(messages.countByStateAndAutomaticFalse(InboxState.HANDLED)).thenReturn(42)
        whenever(messages.countByAutomaticTrue()).thenReturn(24)

        val entry = Inbox(messages, mock(), sent, users).page(" lars ", page, " events@x.nl ").content.single()

        assertThat(entry.senderName).isEqualTo("Lars Mulder")
        assertThat(entry.handledByName).isEqualTo("Alice Board")
        assertThat(entry.answers).isEqualTo(AnsweredEmail(9, "email.contribution-reminder", Instant.EPOCH))
        assertThat(entry.mailbox).isEqualTo("events@x.nl")
        assertThat(Inbox(messages, mock(), sent, users).counts()).isEqualTo(InboxCounts(3, null, 142, 24))
    }

    @Test
    fun `connects with the mailbox's account and reads its folder, and a failed connection waits for the next poll`() {
        val folder: Folder = mock(extraInterfaces = arrayOf(UIDFolder::class))
        whenever((folder as UIDFolder).getMessagesByUID(1, UIDFolder.MAXUID)).thenReturn(arrayOf())
        val store: Store = mock()
        whenever(store.getFolder("INBOX")).thenReturn(folder)
        whenever(store.isConnected).thenReturn(true)
        val session: Session = mock()
        whenever(session.getStore("imaps")).thenReturn(store)
        val cursors: InboxCursorRepository = mock()
        whenever(cursors.findById("INBOX")).thenReturn(Optional.empty())
        val environment: Environment = mock()
        whenever(environment.getProperty("email.bounce.imap.password")).thenReturn("secret")
        val poller =
            InboxPoller(
                MailboxReading(mock(), cursors, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
                "imap.x.nl",
                993,
                "catch@x.nl",
                "INBOX",
                true,
                environment,
            )
        poller.sessionFor = { session }

        poller.poll()
        verify(store).connect("imap.x.nl", 993, "catch@x.nl", "secret")
        verify(store).close()

        poller.sessionFor = { error("no network") }
        poller.poll()
    }

    @Test
    fun `a kept message and a cursor carry what they were given, and hibernate can build them empty`() {
        val kept = InboxMessage("<r>", "<s>", "<s>", "a@x.nl", "Ann", "board@x.nl", "Hi", "text", "<p>html</p>", Instant.EPOCH, true, 9, 5)
        assertThat(listOf(kept.messageId, kept.inReplyTo, kept.fromName, kept.toAddress, kept.bodyText, kept.bodyHtml))
            .containsExactly("<r>", "<s>", "Ann", "board@x.nl", "text", "<p>html</p>")
        assertThat(InboxCursor("INBOX", 7, 3).id).isEqualTo("INBOX")
        assertThat(InboxMessage::class.java.getDeclaredConstructor().newInstance()).isNotNull
        assertThat(InboxCursor::class.java.getDeclaredConstructor().newInstance()).isNotNull
        val poller = InboxPoller(MailboxReading(mock(), mock(), Clock.systemUTC()), "", 993, "", "INBOX", true, mock<Environment>())
        assertThat(poller.sessionFor("imaps").getProperty("mail.store.protocol")).isEqualTo("imaps")
    }
}
