package net.blueshell.api.mail.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.cohort.api.Audience
import net.blueshell.api.cohort.api.CohortAudiences
import net.blueshell.api.mail.domain.Addressee
import net.blueshell.api.mail.domain.Writing
import net.blueshell.api.security.BoardOnly
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.user.api.UserService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@Schema(name = "ReachRequest")
data class ReachRequest(
    val to: List<Addressee> = emptyList(),
)

@Schema(name = "ReachResponse")
data class ReachResponse(
    val recipients: Int,
    @param:Schema(description = "People named who have no email address, so get nothing")
    val withoutEmail: Int,
)

@Schema(name = "WriteEmailRequest")
data class WriteEmailRequest(
    val to: List<Addressee> = emptyList(),
    val subject: String = "",
    @param:Schema(description = "The message as the site's editor writes it, in Discord's markdown")
    val message: String = "",
    val replyTo: String? = null,
    @param:Schema(description = "The added sending address it goes out from; none sends from the site's own")
    val from: Long? = null,
)

@Schema(name = "WrittenResponse")
data class WrittenResponse(
    val sent: Int,
)

/** Writing an email on the site, for the board: to cohorts, roles and people. */
@RestController
@Tag(name = "Mail")
class WritingController(
    private val writing: Writing,
    private val audiences: CohortAudiences,
    private val users: UserService,
    private val currentUser: CurrentUserProvider,
    @param:Value($$"${email.reply-to}") private val defaultReplyTo: String,
) {
    @BoardOnly
    @GetMapping("/mail/audiences")
    fun findAudiences(): List<Audience> = audiences.all()

    /** Where replies may go: the association's own address, or the writer's. */
    @BoardOnly
    @GetMapping("/mail/reply-to")
    fun findReplyToOptions(): List<String> = listOfNotNull(defaultReplyTo, users.findById(writer()).email.ifBlank { null }).distinct()

    @BoardOnly
    @PostMapping("/mail/reach")
    fun findReach(
        @RequestBody request: ReachRequest,
    ): ReachResponse = writing.reach(request.to).let { ReachResponse(it.userIds.size, it.withoutEmail) }

    @BoardOnly
    @PostMapping("/mail/send")
    fun sendWrittenEmail(
        @RequestBody request: WriteEmailRequest,
    ): WrittenResponse =
        WrittenResponse(writing.send(request.to, request.subject, request.message, request.replyTo, writer(), request.from))

    @BoardOnly
    @PostMapping("/mail/test")
    fun sendTestEmail(
        @RequestBody request: WriteEmailRequest,
    ): WrittenResponse = WrittenResponse(writing.test(request.subject, request.message, request.replyTo, writer(), request.from))

    private fun writer(): Long = currentUser.currentUser()?.id ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
}
