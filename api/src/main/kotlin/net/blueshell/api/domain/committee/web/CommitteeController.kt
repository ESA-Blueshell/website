package net.blueshell.api.domain.committee.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.domain.committee.application.CommitteeService
import net.blueshell.api.domain.committee.command.*
import net.blueshell.api.domain.committee.web.dto.CommitteeDetailResponse
import net.blueshell.api.domain.committee.web.dto.CommitteeResponse
import net.blueshell.api.domain.committee.web.dto.CreateCommitteeRequest
import net.blueshell.api.domain.committee.web.dto.UpdateCommitteeRequest
import net.blueshell.api.domain.committee.web.mapping.asDetailResponse
import net.blueshell.api.domain.committee.web.mapping.asSummaryResponse
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.web.AdvancedController
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Committees")
class CommitteeController(
    service: net.blueshell.api.domain.committee.application.CommitteeService,
    private val commandBus: CommandBus
) : AdvancedController<CommitteeService>(
    service
) {
    @GetMapping("/committeeMembers/committees")
    @PermitAll
    fun findCommitteesForCurrentUser(): MutableList<CommitteeResponse> {
        val principalId = principal?.id ?: return mutableListOf()
        val includeAll = hasAuthority(Role.BOARD)
        val committees = commandBus.dispatch(FindCommitteesForCurrentUserCommand(principalId, includeAll))
        return committees.map { it.asDetailResponse() }.toMutableList()
    }

    @GetMapping("/committees")
    @PermitAll
    fun findCommittees(): MutableList<CommitteeResponse> {
        val committees = commandBus.dispatch(FindCommitteesCommand())
        return if (hasAuthority(Role.BOARD)) {
            committees.map { it.asDetailResponse() }.toMutableList()
        } else {
            committees.map { it.asSummaryResponse() }.toMutableList()
        }
    }

    @PreAuthorize("hasPermission(#committeeId, 'Committee', 'read')")
    @GetMapping("/committees/{committeeId}")
    fun findCommitteeById(
        @PathVariable committeeId: Long
    ): CommitteeResponse {
        val committee = commandBus.dispatch(FindCommitteeByIdCommand(committeeId))
        if (hasAuthority(Role.BOARD) || committee.hasMember(principal)) {
            return committee.asDetailResponse()
        }

        return committee.asSummaryResponse()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/committees")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCommittee(@Valid @RequestBody request: @Valid CreateCommitteeRequest): CommitteeDetailResponse {
        val name = requireNotNull(request.name) { "Name is required" }
        val description = requireNotNull(request.description) { "Description is required" }
        val members = requireNotNull(request.members) { "Members are required" }
            .map { CommitteeMemberData(it.userId, it.role) }
            .toMutableList()
        val committee = commandBus.dispatch(CreateCommitteeCommand(name, description, members))
        return committee.asDetailResponse()
    }

    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#id, 'Committee', 'write')")
    @PutMapping(value = ["/committees/{id}"])
    fun updateCommittee(
        @PathVariable id: Long,
        @Valid @RequestBody request: @Valid UpdateCommitteeRequest
    ): CommitteeDetailResponse {
        val name = requireNotNull(request.name) { "Name is required" }
        val description = requireNotNull(request.description) { "Description is required" }
        val members = requireNotNull(request.members) { "Members are required" }
            .map { CommitteeMemberData(it.userId, it.role) }
            .toMutableList()
        val committee = commandBus.dispatch(
            UpdateCommitteeCommand(
                id = id,
                name = name,
                description = description,
                members = members,
                version = request.version
            )
        )
        return committee.asDetailResponse()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping(value = ["/committees/{id}"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCommitteeById(@PathVariable id: Long) {
        commandBus.dispatch(DeleteCommitteeByIdCommand(id))
    }
}
