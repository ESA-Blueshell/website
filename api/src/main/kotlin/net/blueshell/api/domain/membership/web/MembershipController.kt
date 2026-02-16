package net.blueshell.api.domain.membership.web

import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.domain.membership.application.MembershipService
import net.blueshell.api.domain.membership.application.query.MembershipQuery
import net.blueshell.api.domain.membership.command.CreateMembershipCommand
import net.blueshell.api.domain.membership.command.FindMembershipByIdCommand
import net.blueshell.api.domain.membership.command.FindMembershipsCommand
import net.blueshell.api.domain.membership.web.dto.request.BoardCreateMembershipRequest
import net.blueshell.api.domain.membership.web.dto.request.UpdateMembershipRequest
import net.blueshell.api.domain.membership.web.dto.response.MembershipResponse
import net.blueshell.api.domain.membership.web.mapping.asCommand
import net.blueshell.api.domain.membership.web.mapping.asResponse
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.UserPrincipal
import net.blueshell.api.shared.validation.group.Administration
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
    @PreAuthorize("hasPermission(null, 'Membership', 'read')")
    @GetMapping("/memberships")
    fun findMemberships(@ParameterObject query: MembershipQuery = MembershipQuery()): MutableList<MembershipResponse> {
        return commandBus.dispatch(FindMembershipsCommand(query)).map { it.asResponse() }.toMutableList()
    }

    @PreAuthorize("hasPermission(#principal.id, 'User', 'write')")
    @PostMapping("/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    fun createMembership(
        @AuthenticationPrincipal principal: UserPrincipal?
    ): MembershipResponse? {
        val principalId = principal!!.id
        val membership = commandBus.dispatch(
            CreateMembershipCommand(
                principalId = principalId,
                isMember = principal.hasAuthority(Role.MEMBER),
                hasAddress = principal.addressId != null
            )
        )
        return membership.asResponse()
    }

    @PreAuthorize("hasPermission(null, 'Membership', 'write')")
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

    @PreAuthorize("hasPermission(#id, 'Membership', 'read')")
    @GetMapping(value = ["/memberships/{id}"])
    fun findMembershipById(@PathVariable id: Long): MembershipResponse {
        return commandBus.dispatch(FindMembershipByIdCommand(id)).asResponse()
    }
}
