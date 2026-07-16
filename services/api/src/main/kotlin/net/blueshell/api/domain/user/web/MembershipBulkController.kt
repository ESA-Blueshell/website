package net.blueshell.api.domain.user.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.domain.user.command.ExecuteBulkEndMembershipCommand
import net.blueshell.api.domain.user.command.PreviewBulkEndMembershipCommand
import net.blueshell.api.domain.user.web.dto.request.BulkEndMembershipRequest
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.dto.bulk.BulkActionResult
import net.blueshell.api.shared.dto.bulk.BulkPreviewResult
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * Bulk end-membership over a set of users. Two-phase: `preview` shows who has an
 * endable active membership vs is skipped; `execute` ends all active memberships
 * (effective today) in a single request. Board-only. Period-independent.
 */
@RestController
@Tag(name = "Memberships")
class MembershipBulkController(private val commandBus: CommandBus) {

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Membership', 'write')")
    @PostMapping("/memberships/bulk/end/preview")
    fun previewBulkEnd(@Valid @RequestBody request: BulkEndMembershipRequest): BulkPreviewResult =
        commandBus.dispatch(PreviewBulkEndMembershipCommand(userIds = request.userIds))

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Membership', 'write')")
    @PostMapping("/memberships/bulk/end/execute")
    fun executeBulkEnd(@Valid @RequestBody request: BulkEndMembershipRequest): BulkActionResult =
        commandBus.dispatch(ExecuteBulkEndMembershipCommand(userIds = request.userIds))
}
