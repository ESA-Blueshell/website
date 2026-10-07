package net.blueshell.api.mail.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.mail.domain.Answering
import net.blueshell.api.mail.domain.Conversation
import net.blueshell.api.mail.domain.Inbox
import net.blueshell.api.mail.domain.InboxCounts
import net.blueshell.api.mail.domain.InboxEntry
import net.blueshell.api.security.BoardOnly
import net.blueshell.api.shared.security.CurrentUserProvider
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Schema(name = "ReplyRequest")
data class ReplyRequest(
    @param:Schema(description = "The reply as the site's editor writes it, in Discord's markdown")
    val message: String = "",
    val replyTo: String? = null,
)

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
    private val answering: Answering,
    private val currentUser: CurrentUserProvider,
) {
    @BoardOnly
    @GetMapping("/mail/inbox")
    fun findInbox(
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) mailbox: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "RECEIVED") sort: InboxSort = InboxSort.RECEIVED,
        @RequestParam(defaultValue = "true") descending: Boolean = true,
    ): Page<InboxEntry> {
        val direction = if (descending) Sort.Direction.DESC else Sort.Direction.ASC
        // The id settles messages that tie, so a page never repeats or skips one.
        val order = Sort.by(direction, sort.property).and(Sort.by(Sort.Direction.DESC, "id"))
        return inbox.page(search, PageRequest.of(page.coerceAtLeast(0), PAGE_SIZE, order), mailbox)
    }

    @BoardOnly
    @GetMapping("/mail/inbox/{id}")
    fun findConversation(
        @PathVariable id: Long,
    ): Conversation = inbox.conversation(id)

    /** Sends the reply threaded with the conversation; it shows in Sent and on the conversation. */
    @BoardOnly
    @PostMapping("/mail/inbox/{id}/reply")
    fun replyToMessage(
        @PathVariable id: Long,
        @RequestBody request: ReplyRequest,
    ): Conversation {
        answering.reply(id, request.message, request.replyTo, currentUser.currentUser()?.id)
        return inbox.conversation(id)
    }

    @BoardOnly
    @PostMapping("/mail/inbox/{id}/handled")
    fun markMessageHandled(
        @PathVariable id: Long,
    ): Conversation {
        answering.markHandled(id, currentUser.currentUser()?.id)
        return inbox.conversation(id)
    }

    @BoardOnly
    @GetMapping("/mail/inbox/counts")
    fun findInboxCounts(): InboxCounts = inbox.counts()

    private companion object {
        const val PAGE_SIZE = 50
    }
}
