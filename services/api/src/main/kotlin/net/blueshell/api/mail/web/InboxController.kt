package net.blueshell.api.mail.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.mail.domain.Inbox
import net.blueshell.api.mail.domain.InboxCounts
import net.blueshell.api.mail.domain.InboxEntry
import net.blueshell.api.security.BoardOnly
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** What the inbox can be ordered by, each naming the message's field it reads. */
@Schema(enumAsRef = true)
enum class InboxSort(
    val property: String,
) {
    RECEIVED("receivedAt"),
    FROM("fromName"),
    SUBJECT("subject"),
    STATE("state"),
}

/** The catch-all mailbox as the board reads it, newest first unless asked otherwise. */
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
        @RequestParam(defaultValue = "RECEIVED") sort: InboxSort = InboxSort.RECEIVED,
        @RequestParam(defaultValue = "true") descending: Boolean = true,
    ): Page<InboxEntry> {
        val direction = if (descending) Sort.Direction.DESC else Sort.Direction.ASC
        // The id settles messages that tie, so a page never repeats or skips one.
        val order = Sort.by(direction, sort.property).and(Sort.by(Sort.Direction.DESC, "id"))
        return inbox.page(search, PageRequest.of(page.coerceAtLeast(0), PAGE_SIZE, order))
    }

    @BoardOnly
    @GetMapping("/mail/inbox/counts")
    fun findInboxCounts(): InboxCounts = inbox.counts()

    private companion object {
        const val PAGE_SIZE = 50
    }
}
