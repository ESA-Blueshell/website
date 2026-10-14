package net.blueshell.api.mail.web

import net.blueshell.api.mail.domain.Inbox
import net.blueshell.api.mail.domain.InboxCounts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

class InboxControllerTest {
    private val inbox: Inbox = mock()
    private val controller = InboxController(inbox)

    @Test
    fun `reads a page of fifty, newest first, and the counts`() {
        val newestFirst = Sort.by(Sort.Order.desc("receivedAt"), Sort.Order.desc("id"))
        whenever(inbox.page("x", PageRequest.of(2, 50, newestFirst))).thenReturn(Page.empty())
        whenever(inbox.counts()).thenReturn(InboxCounts(1, null, 2, 3))

        assertThat(controller.findInbox("x", 2).content).isEmpty()
        assertThat(controller.findInboxCounts().automatic).isEqualTo(3)
    }

    @Test
    fun `orders the page by what the reader picked, and settles ties by id`() {
        val bySender = Sort.by(Sort.Order.asc("fromName"), Sort.Order.desc("id"))
        whenever(inbox.page(null, PageRequest.of(0, 50, bySender))).thenReturn(Page.empty())

        assertThat(controller.findInbox(null, 0, InboxSort.FROM, descending = false).content).isEmpty()
        assertThat(InboxSort.entries.map { it.property }).containsExactly("receivedAt", "fromName", "subject", "state")
    }
}
