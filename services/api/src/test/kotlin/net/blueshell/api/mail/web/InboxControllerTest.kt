package net.blueshell.api.mail.web

import net.blueshell.api.mail.domain.Answering
import net.blueshell.api.mail.domain.Conversation
import net.blueshell.api.mail.domain.Inbox
import net.blueshell.api.mail.domain.InboxCounts
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.shared.security.CurrentUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest

class InboxControllerTest {
    private val inbox: Inbox = mock()
    private val answering: Answering = mock()
    private val currentUser: CurrentUserProvider = mock()
    private val controller = InboxController(inbox, answering, currentUser)

    @Test
    fun `reads a page of fifty and the counts`() {
        whenever(inbox.page("x", PageRequest.of(2, 50))).thenReturn(Page.empty())
        whenever(inbox.counts()).thenReturn(InboxCounts(1, null, 2, 3))

        assertThat(controller.findInbox("x", 2).content).isEmpty()
        assertThat(controller.findInboxCounts().automatic).isEqualTo(3)
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
