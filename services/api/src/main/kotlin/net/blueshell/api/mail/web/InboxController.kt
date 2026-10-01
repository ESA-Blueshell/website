package net.blueshell.api.mail.web

import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.mail.domain.Inbox
import net.blueshell.api.mail.domain.InboxCounts
import net.blueshell.api.mail.domain.InboxEntry
import net.blueshell.api.security.BoardOnly
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** The catch-all mailbox as the board reads it, newest first. */
@RestController
@Tag(name = "Mail")
class InboxController(
    private val inbox: Inbox,
) {
    @BoardOnly
    @GetMapping("/mail/inbox")
    fun findInbox(
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "0") page: Int,
    ): Page<InboxEntry> = inbox.page(search, PageRequest.of(page.coerceAtLeast(0), PAGE_SIZE))

    @BoardOnly
    @GetMapping("/mail/inbox/counts")
    fun findInboxCounts(): InboxCounts = inbox.counts()

    private companion object {
        const val PAGE_SIZE = 50
    }
}
