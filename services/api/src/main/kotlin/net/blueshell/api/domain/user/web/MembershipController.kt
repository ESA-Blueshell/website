package net.blueshell.api.domain.user.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.query.MembershipQuery
import net.blueshell.api.domain.user.command.SubmitMembershipApplicationCommand
import net.blueshell.api.domain.user.web.dto.request.MembershipApplicationRequest
import net.blueshell.api.domain.user.web.dto.response.SignupOutcomeResponse
import net.blueshell.api.domain.user.command.DeleteMembershipCommand
import net.blueshell.api.domain.user.command.EndMembershipCommand
import net.blueshell.api.domain.user.command.FindDeletedMembershipsCommand
import net.blueshell.api.domain.user.command.FindMembershipByIdCommand
import net.blueshell.api.domain.user.command.FindMembershipsCommand
import net.blueshell.api.domain.user.command.ReopenMembershipCommand
import net.blueshell.api.domain.user.command.RestoreMembershipCommand
import net.blueshell.api.domain.user.web.dto.request.BoardCreateMembershipRequest
import net.blueshell.api.domain.user.web.dto.request.UpdateMembershipRequest
import net.blueshell.api.domain.user.web.dto.response.MembershipResponse
import net.blueshell.api.domain.user.web.mapping.request.asCommand
import net.blueshell.api.domain.user.web.mapping.response.asResponse
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipal
import net.blueshell.api.domain.user.web.validation.group.Administration
import net.blueshell.api.shared.web.BaseController
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping
@Tag(name = "Memberships")
class MembershipController(
    private val membershipService: MembershipService,
    private val commandBus: CommandBus
) : BaseController<MembershipService>(membershipService) {
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Membership', 'read')")
    @GetMapping("/memberships")
    fun findMemberships(@ParameterObject query: MembershipQuery = MembershipQuery()): MutableList<MembershipResponse> {
        return commandBus.dispatch(FindMembershipsCommand(query)).map { it.asResponse() }.toMutableList()
    }

    @PreAuthorize("hasPermission(#principal.id, 'User', 'write')")
    @PostMapping("/memberships")
    fun createMembership(
        @Valid @RequestBody request: MembershipApplicationRequest,
        @AuthenticationPrincipal principal: UserPrincipal?
    ): SignupOutcomeResponse {
        val outcome = commandBus.dispatch(
            SubmitMembershipApplicationCommand(
                userId = principal!!.id,
                conditionsAccepted = request.conditionsAccepted,
            )
        )
        return SignupOutcomeResponse(outcome.emailConfirmed, outcome.membershipStarted)
    }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Membership', 'write')")
    @PostMapping("/users/{userId}/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    fun boardCreateMembership(
        @Validated(Administration::class) @RequestBody request: BoardCreateMembershipRequest,
        @PathVariable(required = true) userId: Long
    ): MembershipResponse? {
        val membership = commandBus.dispatch(request.asCommand())
        return membership.asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Membership', 'write')")
    @PutMapping(value = ["/memberships/{id}"])
    fun updateMembership(@PathVariable id: Long, @RequestBody request: UpdateMembershipRequest): MembershipResponse? {
        val membership = commandBus.dispatch(request.asCommand(id))
        return membership.asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Membership', 'write')")
    @PostMapping(value = ["/memberships/{id}/end"])
    fun endMembership(@PathVariable id: Long): MembershipResponse {
        val membership = commandBus.dispatch(EndMembershipCommand(id))
        return membership.asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Membership', 'write')")
    @PostMapping(value = ["/memberships/{id}/reopen"])
    fun reopenMembership(@PathVariable id: Long): MembershipResponse {
        val membership = commandBus.dispatch(ReopenMembershipCommand(id))
        return membership.asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Membership', 'read')")
    @GetMapping(value = ["/memberships/{id}"])
    fun findMembershipById(@PathVariable id: Long): MembershipResponse {
        return commandBus.dispatch(FindMembershipByIdCommand(id)).asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Membership', 'delete')")
    @DeleteMapping(value = ["/memberships/{id}"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMembership(@PathVariable id: Long) {
        commandBus.dispatch(DeleteMembershipCommand(id))
    }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Membership', 'restore')")
    @PutMapping(value = ["/memberships/{id}/restore"])
    fun restoreMembership(@PathVariable id: Long): MembershipResponse =
        commandBus.dispatch(RestoreMembershipCommand(id)).asResponse()

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Membership', 'read-deleted')")
    @GetMapping(value = ["/users/{userId}/memberships/deleted"])
    fun findDeletedMemberships(@PathVariable userId: Long): MutableList<MembershipResponse> =
        commandBus.dispatch(FindDeletedMembershipsCommand(userId)).map { it.asResponse() }.toMutableList()
}
