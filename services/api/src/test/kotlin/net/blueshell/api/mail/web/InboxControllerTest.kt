package net.blueshell.api.mail.web

import net.blueshell.api.mail.domain.Answering
import net.blueshell.api.mail.domain.Conversation
import net.blueshell.api.mail.domain.Inbox
import net.blueshell.api.mail.domain.InboxCounts
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

class InboxControllerTest {
    private val inbox: Inbox = mock()
    private val answering: Answering = mock()
    private val currentUser: CurrentUserProvider = mock()
    private val controller = InboxController(inbox, answering, currentUser)

    @Test
    fun `reads a page of fifty, newest first, and the counts`() {
        val newestFirst = Sort.by(Sort.Order.desc("receivedAt"), Sort.Order.desc("id"))
        whenever(inbox.page("x", PageRequest.of(2, 50, newestFirst), "events@b.nl")).thenReturn(Page.empty())
        whenever(inbox.counts()).thenReturn(InboxCounts(1, null, 2, 3))

        assertThat(controller.findInbox("x", "events@b.nl", 2).content).isEmpty()
        assertThat(controller.findInboxCounts().automatic).isEqualTo(3)
    }

    @Test
    fun `orders the page by what the reader picked, and settles ties by id`() {
        val bySender = Sort.by(Sort.Order.asc("fromName"), Sort.Order.desc("id"))
        whenever(inbox.page(null, PageRequest.of(0, 50, bySender), null)).thenReturn(Page.empty())

        assertThat(controller.findInbox(null, null, 0, InboxSort.FROM, descending = false).content).isEmpty()
        assertThat(InboxSort.entries.map { it.property }).containsExactly("receivedAt", "fromName", "subject", "state")
    }

    @Test
    fun `replies and marks handled as the board member signed in, and answers the conversation`() {
        val conversation: Conversation = mock()
        whenever(inbox.conversation(1)).thenReturn(conversation)
        whenever(currentUser.currentUser()).thenReturn(CurrentUser(id = 6, roles = emptySet(), addressId = null))

        assertThat(controller.findConversation(1)).isSameAs(conversation)
        assertThat(controller.replyToMessage(1, ReplyRequest("Thanks", "board@b.nl"))).isSameAs(conversation)
        assertThat(controller.markMessageHandled(1)).isSameAs(conversation)
        verify(answering).reply(1, "Thanks", "board@b.nl", 6)
        verify(answering).markHandled(1, 6)
        assertThat(ReplyRequest().message).isEmpty()
    }
}
