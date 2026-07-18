package net.blueshell.api.domain.committee.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.domain.committee.application.CommitteeService
import net.blueshell.api.domain.committee.command.*
import net.blueshell.api.domain.committee.web.dto.request.CreateCommitteeRequest
import net.blueshell.api.domain.committee.web.dto.request.UpdateCommitteeRequest
import net.blueshell.api.domain.committee.web.dto.response.CommitteeDetailResponse
import net.blueshell.api.domain.committee.web.dto.response.CommitteeResponse
import net.blueshell.api.domain.committee.web.mapping.request.asCommand
import net.blueshell.api.domain.committee.web.mapping.response.asDetailResponse
import net.blueshell.api.domain.committee.web.mapping.response.asSummaryResponse
import net.blueshell.api.shared.security.UserPrincipal
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.web.AdvancedController
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Committees")
class CommitteeController(
    service: CommitteeService,
    private val commandBus: CommandBus
) : AdvancedController<CommitteeService>(
    service
) {
    @GetMapping("/committeeMembers/committees")
    @PermitAll
    fun findCommitteesByUserId(
        @AuthenticationPrincipal principal: UserPrincipal?
    ): MutableList<CommitteeResponse> {
        val principalId = principal?.id ?: return mutableListOf()
        val includeAll = principal.hasAuthority(Role.BOARD)
        val committees = commandBus.dispatch(FindCommitteesForCurrentUserCommand(principalId, includeAll))
        return committees.map { it.asDetailResponse() }.toMutableList()
    }

    @GetMapping("/committees")
    @PermitAll
    // CodeQL false positive: `principal` is Spring-injected from the validated session, not user input; it only picks DTO detail level.
    @Suppress("codeql[java/user-controlled-bypass]")
    fun findCommittees(
        @AuthenticationPrincipal principal: UserPrincipal?
    ): MutableList<CommitteeResponse> {
        val committees = commandBus.dispatch(FindCommitteesCommand())
        return if (principal?.hasAuthority(Role.BOARD) == true) {
            committees.map { it.asDetailResponse() }.toMutableList()
        } else {
            committees.map { it.asSummaryResponse() }.toMutableList()
        }
    }

    @PreAuthorize("hasPermission(#committeeId, 'Committee', 'read')")
    @GetMapping("/committees/{committeeId}")
    // CodeQL false positive: `principal` is Spring-injected from the validated session, not user input; access is already gated by @PreAuthorize.
    @Suppress("codeql[java/user-controlled-bypass]")
    fun findCommitteeById(
        @PathVariable committeeId: Long,
        @AuthenticationPrincipal principal: UserPrincipal?
    ): CommitteeResponse {
        val committee = commandBus.dispatch(FindCommitteeByIdCommand(committeeId))
        if (principal?.hasAuthority(Role.BOARD) == true || committee.hasMember(principal?.id)) {
            return committee.asDetailResponse()
        }

        return committee.asSummaryResponse()
    }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Committee', 'write')")
    @PostMapping("/committees")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCommittee(@Valid @RequestBody request: @Valid CreateCommitteeRequest): CommitteeDetailResponse {
        val committee = commandBus.dispatch(request.asCommand())
        return committee.asDetailResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Committee', 'write')")
    @PutMapping(value = ["/committees/{id}"])
    fun updateCommittee(
        @PathVariable id: Long,
        @Valid @RequestBody request: @Valid UpdateCommitteeRequest
    ): CommitteeDetailResponse {
        val committee = commandBus.dispatch(request.asCommand(id))
        return committee.asDetailResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Committee', 'delete')")
    @DeleteMapping(value = ["/committees/{id}"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCommitteeById(@PathVariable id: Long) {
        commandBus.dispatch(DeleteCommitteeByIdCommand(id))
    }
}
