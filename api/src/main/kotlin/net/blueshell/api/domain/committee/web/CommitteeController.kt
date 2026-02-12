package net.blueshell.api.domain.committee.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.domain.committee.application.CommitteeService
import net.blueshell.api.domain.committee.web.dto.CommitteeDetailResponse
import net.blueshell.api.domain.committee.web.dto.CreateCommitteeRequest
import net.blueshell.api.domain.committee.web.dto.UpdateCommitteeRequest
import net.blueshell.api.domain.committee.web.mapping.asDetailResponse
import net.blueshell.api.domain.committee.web.mapping.asEntity
import net.blueshell.api.domain.committee.web.mapping.asSummaryResponse
import net.blueshell.api.shared.dto.BaseDTO
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.web.AdvancedController
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Committees")
class CommitteeController(
    service: CommitteeService
) : AdvancedController<CommitteeService>(
    service
) {
    @GetMapping("/committeeMembers/committees")
    @PermitAll
    fun findCommitteesForCurrentUser(): MutableList<out BaseDTO> {
        val principalId = principal?.id ?: return mutableListOf()
        if (hasAuthority(Role.BOARD)) {
            return service.findAll().map { it.asDetailResponse() }.toMutableList()
        }

        val committees = service.findAllByUserId(principalId)
        return committees.map { it.asDetailResponse() }.toMutableList()
    }

    @GetMapping("/committees")
    @PermitAll
    fun findCommittees(): MutableList<out BaseDTO> {
        if (hasAuthority(Role.BOARD)) {
            return service.findAll().map { it.asDetailResponse() }.toMutableList()
        }

        return service.findAll().map { it.asSummaryResponse() }.toMutableList()
    }

    @PreAuthorize("hasPermission(#committeeId, 'Committee', 'read')")
    @GetMapping("/committees/{committeeId}")
    fun findCommitteeById(
        @PathVariable committeeId: Long
    ): BaseDTO {
        val committee = service.findById(committeeId)
        if (hasAuthority(Role.BOARD) || committee.hasMember(principal)) {
            return committee.asDetailResponse()
        }

        return committee.asSummaryResponse()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/committees")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCommittee(@Valid @RequestBody request: @Valid CreateCommitteeRequest): CommitteeDetailResponse {
        var committee = request.asEntity()
        committee = service.create(committee)
        return committee.asDetailResponse()
    }

    @PreAuthorize("hasAuthority('BOARD') || (#id == #dto.id && hasPermission(#id, 'Committee', 'write'))")
    @PutMapping(value = ["/committees/{id}"])
    fun updateCommittee(
        @PathVariable id: Long,
        @Valid @RequestBody request: @Valid UpdateCommitteeRequest
    ): CommitteeDetailResponse {
        var committee = service.findById(id)
        committee = request.asEntity(committee)
        committee = service.update(committee)
        return committee.asDetailResponse()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping(value = ["/committees/{id}"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCommitteeById(@PathVariable id: Long) {
        service.deleteById(id)
    }
}
