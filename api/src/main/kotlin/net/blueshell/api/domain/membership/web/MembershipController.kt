package net.blueshell.api.domain.membership.web

import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.domain.membership.command.*
import net.blueshell.api.domain.membership.persistence.filter.MembershipFilter
import net.blueshell.api.domain.membership.web.dto.BoardCreateMembershipRequest
import net.blueshell.api.domain.membership.web.dto.MembershipResponse
import net.blueshell.api.domain.membership.web.dto.UpdateMembershipRequest
import net.blueshell.api.domain.membership.web.mapping.asResponse
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.web.BaseController
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping
@Tag(name = "Memberships")
class MembershipController(
    service: net.blueshell.api.domain.membership.application.MembershipService,
    private val commandBus: CommandBus
) : BaseController<net.blueshell.api.domain.membership.application.MembershipService>(service) {
    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("/memberships")
    fun findMemberships(@ParameterObject filter: MembershipFilter): MutableList<MembershipResponse> {
        return commandBus.dispatch(FindMembershipsCommand(filter)).map { it.asResponse() }.toMutableList()
    }

    @PreAuthorize("hasAuthority('GUEST')")
    @PostMapping("/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    fun createMembership(): MembershipResponse? {
        val membership = commandBus.dispatch(
            CreateMembershipCommand(
                principalId = principal?.id,
                isMember = hasAuthority(Role.MEMBER),
                hasAddress = principal?.addressId != null
            )
        )
        return membership.asResponse()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("memberships/member")
    @ResponseStatus(HttpStatus.CREATED)
    fun boardCreateMembership(
        @Validated(net.blueshell.api.shared.validation.group.Administration::class) @RequestBody request: BoardCreateMembershipRequest
    ): MembershipResponse? {
        val membership = commandBus.dispatch(
            BoardCreateMembershipCommand(
                userId = requireNotNull(request.userId) { "User id is required" },
                memberType = requireNotNull(request.memberType) { "Member type is required" },
                startDate = requireNotNull(request.startDate) { "Start date is required" },
                endDate = request.endDate,
                incasso = requireNotNull(request.incasso) { "Incasso is required" }
            )
        )
        return membership.asResponse()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PutMapping(value = ["/{id}"])
    fun updateMembership(@PathVariable id: Long, @RequestBody request: UpdateMembershipRequest): MembershipResponse? {
        val membership = commandBus.dispatch(
            UpdateMembershipCommand(
                id = id,
                userId = requireNotNull(request.userId) { "User id is required" },
                memberType = request.memberType,
                startDate = requireNotNull(request.startDate) { "Start date is required" },
                endDate = request.endDate,
                incasso = request.incasso,
                version = request.version
            )
        )
        return membership.asResponse()
    }

    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#id, 'Membership', 'read')")
    @GetMapping(value = ["/{id}"])
    fun findMembershipById(@PathVariable id: Long): MembershipResponse {
        return commandBus.dispatch(FindMembershipByIdCommand(id)).asResponse()
    }
}
