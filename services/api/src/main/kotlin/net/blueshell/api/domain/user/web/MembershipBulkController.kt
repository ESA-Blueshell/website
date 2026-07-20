package net.blueshell.api.domain.user.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.domain.user.command.ExecuteBulkEndMembershipCommand
import net.blueshell.api.domain.user.command.ExecuteBulkResumeMembershipCommand
import net.blueshell.api.domain.user.web.dto.request.BulkEndMembershipRequest
import net.blueshell.api.domain.user.web.dto.request.BulkResumeMembershipRequest
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.dto.bulk.BulkActionResult
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * Bulk end-membership and resume-membership actions over a set of users.
 * Execute-only endpoints; previews are computed frontend-side.
 * Board-only. Period-independent. See docs/proposals/bulk-actions/REDESIGN.md §2.
 */
@RestController
@Tag(name = "Memberships")
class MembershipBulkController(private val commandBus: CommandBus) {

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Membership', 'write')")
    @PostMapping("/memberships/bulk/end/execute")
    fun executeBulkEnd(@Valid @RequestBody request: BulkEndMembershipRequest): BulkActionResult =
        commandBus.dispatch(ExecuteBulkEndMembershipCommand(userIds = request.userIds))

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Membership', 'write')")
    @PostMapping("/memberships/bulk/resume/execute")
    fun executeBulkResume(@Valid @RequestBody request: BulkResumeMembershipRequest): BulkActionResult =
        commandBus.dispatch(ExecuteBulkResumeMembershipCommand(userIds = request.userIds))
}
