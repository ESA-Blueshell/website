package net.blueshell.api.domain.user.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.MembershipUseCases
import net.blueshell.api.domain.user.application.query.MembershipQuery
import net.blueshell.api.domain.user.web.dto.request.BoardCreateMembershipRequest
import net.blueshell.api.domain.user.web.dto.request.MembershipApplicationRequest
import net.blueshell.api.domain.user.web.dto.request.UpdateMembershipRequest
import net.blueshell.api.domain.user.web.dto.response.MembershipResponse
import net.blueshell.api.domain.user.web.dto.response.SignupOutcomeResponse
import net.blueshell.api.domain.user.web.mapping.response.asResponse
import net.blueshell.api.shared.security.UserPrincipal
import net.blueshell.api.shared.web.BaseController
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@Validated
@RequestMapping
@Tag(name = "Memberships")
class MembershipController(
    private val membershipService: MembershipService,
    private val useCases: MembershipUseCases,
) : BaseController<MembershipService>(membershipService) {
    @PreAuthorize("hasPermission('__NO_TARGET__', 'Membership', 'read')")
    @GetMapping("/memberships")
    fun findMemberships(@ParameterObject query: MembershipQuery = MembershipQuery()): MutableList<MembershipResponse> {
        return useCases.findByQuery(query).map { it.asResponse() }.toMutableList()
    }

    @PreAuthorize("hasPermission(#principal.id, 'User', 'write')")
    @PostMapping("/memberships")
    fun createMembership(
        @Valid @RequestBody request: MembershipApplicationRequest,
        @AuthenticationPrincipal principal: UserPrincipal?
    ): SignupOutcomeResponse {
        val outcome = useCases.apply(principal!!.id)
        return SignupOutcomeResponse(outcome.emailConfirmed, outcome.membershipStarted)
    }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Membership', 'write')")
    @PostMapping("/users/{userId}/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    fun boardCreateMembership(
        @Valid @RequestBody request: BoardCreateMembershipRequest,
        @PathVariable(required = true) userId: Long
    ): MembershipResponse? {
        val membership = useCases.boardCreate(
            userId = request.userId,
            memberType = request.memberType,
            startDate = request.startDate,
            endDate = request.endDate,
            incasso = request.incasso,
        )
        return membership.asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Membership', 'write')")
    @PutMapping(value = ["/memberships/{id}"])
    fun updateMembership(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateMembershipRequest
    ): MembershipResponse? {
        val membership = useCases.correct(
            id = id,
            userId = request.userId,
            memberType = request.memberType,
            startDate = request.startDate,
            endDate = request.endDate,
            incasso = request.incasso,
            version = request.version,
        )
        return membership.asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Membership', 'write')")
    @PostMapping(value = ["/memberships/{id}/end"])
    fun endMembership(@PathVariable @Positive id: Long): MembershipResponse {
        return useCases.end(id).asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Membership', 'write')")
    @PostMapping(value = ["/memberships/{id}/reopen"])
    fun reopenMembership(@PathVariable @Positive id: Long): MembershipResponse {
        return useCases.reopen(id).asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Membership', 'read')")
    @GetMapping(value = ["/memberships/{id}"])
    fun findMembershipById(@PathVariable @Positive id: Long): MembershipResponse {
        return useCases.findById(id).asResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Membership', 'delete')")
    @DeleteMapping(value = ["/memberships/{id}"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMembership(@PathVariable @Positive id: Long) {
        useCases.delete(id)
    }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Membership', 'restore')")
    @PutMapping(value = ["/memberships/{id}/restore"])
    fun restoreMembership(@PathVariable @Positive id: Long): MembershipResponse =
        useCases.restore(id).asResponse()

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Membership', 'read-deleted')")
    @GetMapping(value = ["/users/{userId}/memberships/deleted"])
    fun findDeletedMemberships(@PathVariable @Positive userId: Long): MutableList<MembershipResponse> =
        useCases.findDeletedByUserId(userId).map { it.asResponse() }.toMutableList()
}
