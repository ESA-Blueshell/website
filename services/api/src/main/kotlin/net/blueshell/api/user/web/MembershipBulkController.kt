package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.shared.dto.bulk.BulkActionResult
import net.blueshell.api.user.domain.BulkMembershipOperation
import net.blueshell.api.user.domain.BulkMembershipPreview
import net.blueshell.api.user.domain.BulkMembershipUseCases
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * Ends memberships for a whole selection at once, board-only.
 *
 * The preview and the apply are the same decision read twice, so the dialog can name every
 * selected member and say what will happen to them before anything is written. Neither is
 * tied to a contribution period: members leave on their own schedule.
 */
@RestController
@Tag(name = "Memberships")
class MembershipBulkController(
    private val useCases: BulkMembershipUseCases,
) {

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Membership', 'write')")
    @PostMapping("/memberships/bulk/end/preview")
    fun previewBulkEnd(@Valid @RequestBody request: BulkEndMembershipRequest): BulkMembershipPreview =
        useCases.preview(request.userIds, BulkMembershipOperation.END)

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Membership', 'write')")
    @PostMapping("/memberships/bulk/end")
    fun endMemberships(@Valid @RequestBody request: BulkEndMembershipRequest): BulkActionResult =
        useCases.execute(request.userIds, BulkMembershipOperation.END)
}
