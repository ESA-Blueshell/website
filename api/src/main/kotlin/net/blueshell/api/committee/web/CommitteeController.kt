package net.blueshell.api.committee.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.web.AdvancedController
import net.blueshell.api.shared.dto.BaseDTO
import net.blueshell.api.committee.web.dto.AdvancedCommitteeDTO
import net.blueshell.api.committee.application.CommitteeService
import net.blueshell.api.committee.persistence.asAdvancedDto
import net.blueshell.api.committee.persistence.asSimpleDto
import net.blueshell.api.committee.web.dto.asEntity
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
            return service.findAll().map { it.asAdvancedDto() }.toMutableList()
        }

        val committees = service.findAllByUserId(principalId)
        return committees.map { it.asAdvancedDto() }.toMutableList()
    }

    @GetMapping("/committees")
    @PermitAll
    fun findCommittees(): MutableList<out BaseDTO> {
        if (hasAuthority(Role.BOARD)) {
            return service.findAll().map { it.asAdvancedDto() }.toMutableList()
        }

        return service.findAll().map { it.asSimpleDto() }.toMutableList()
    }

    @PreAuthorize("hasPermission(#committeeId, 'Committee', 'read')")
    @GetMapping("/committees/{committeeId}")
    fun findCommitteeById(
        @PathVariable committeeId: Long
    ): BaseDTO {
        val committee = service.findById(committeeId)
        if (hasAuthority(Role.BOARD) || committee.hasMember(principal)) {
            return committee.asAdvancedDto()
        }

        return committee.asSimpleDto()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/committees")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCommittee(@Valid @RequestBody advancedCommitteeDTO: @Valid AdvancedCommitteeDTO): AdvancedCommitteeDTO {
        var committee = advancedCommitteeDTO.asEntity()
        committee = service.create(committee)
        return committee.asAdvancedDto()
    }

    @PreAuthorize("hasAuthority('BOARD') || (#id == #dto.id && hasPermission(#id, 'Committee', 'write'))")
    @PutMapping(value = ["/committees/{id}"])
    fun updateCommittee(
        @PathVariable id: Long,
        @Valid @RequestBody dto: @Valid AdvancedCommitteeDTO
    ): AdvancedCommitteeDTO {
        var committee = service.findById(id)
        dto.asEntity(committee)
        committee = service.update(committee)
        return committee.asAdvancedDto()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping(value = ["/committees/{id}"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCommitteeById(@PathVariable id: Long) {
        service.deleteById(id)
    }
}
