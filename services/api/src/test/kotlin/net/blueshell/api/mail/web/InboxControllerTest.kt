package net.blueshell.api.mail.web

import net.blueshell.api.mail.domain.Inbox
import net.blueshell.api.mail.domain.InboxCounts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest

class InboxControllerTest {
    @Test
    fun `reads a page of fifty and the counts`() {
        val inbox: Inbox = mock()
        whenever(inbox.page("x", PageRequest.of(2, 50))).thenReturn(Page.empty())
        whenever(inbox.counts()).thenReturn(InboxCounts(1, null, 2, 3))
        val controller = InboxController(inbox)

        assertThat(controller.findInbox("x", 2).content).isEmpty()
        assertThat(controller.findInboxCounts().automatic).isEqualTo(3)
    }
}
