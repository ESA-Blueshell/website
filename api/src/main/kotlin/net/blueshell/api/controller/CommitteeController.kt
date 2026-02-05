package net.blueshell.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.controller.base.AdvancedController
import net.blueshell.api.dto.base.BaseDTO
import net.blueshell.api.common.enums.Role
import net.blueshell.api.dto.committee.AdvancedCommitteeDTO
import net.blueshell.api.mapper.committee.AdvancedCommitteeMapper
import net.blueshell.api.mapper.committee.SimpleCommitteeMapper
import net.blueshell.api.service.CommitteeService
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Committees")
class CommitteeController(
    service: CommitteeService,
    advancedCommitteeMapper: AdvancedCommitteeMapper,
    simpleCommitteeMapper: SimpleCommitteeMapper
) : AdvancedController<CommitteeService, AdvancedCommitteeMapper, SimpleCommitteeMapper>(
    service,
    advancedCommitteeMapper,
    simpleCommitteeMapper
) {
    @GetMapping("/committeeMembers/committees")
    @PermitAll
    fun findCommitteesForCurrentUser(): MutableList<out BaseDTO> {
        val principalId = principal?.id ?: return mutableListOf()
        if (hasAuthority(Role.BOARD)) {
            return advancedMapper.toDTOs(service.findAll())
        }

        val committees = service.findAllByUserId(principalId)
        return advancedMapper.toDTOs(committees)
    }

    @GetMapping("/committees")
    @PermitAll
    fun findCommittees(): MutableList<out BaseDTO> {
        if (hasAuthority(Role.BOARD)) {
            return advancedMapper.toDTOs(service.findAll())
        }

        return simpleMapper.toDTOs(service.findAll())
    }

    @PreAuthorize("hasPermission(#committeeId, 'Committee', 'read')")
    @GetMapping("/committees/{committeeId}")
    fun findCommitteeById(
        @PathVariable committeeId: Long
    ): BaseDTO {
        val committee = service.findById(committeeId)
        if (hasAuthority(Role.BOARD) || committee.hasMember(principal)) {
            return advancedMapper.toDTO(committee)
        }

        return simpleMapper.toDTO(committee)
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/committees")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCommittee(@Valid @RequestBody advancedCommitteeDTO: @Valid AdvancedCommitteeDTO): AdvancedCommitteeDTO {
        var committee = advancedMapper.fromDTO(advancedCommitteeDTO)
        committee = service.create(committee)
        return advancedMapper.toDTO(committee)
    }

    @PreAuthorize("hasAuthority('BOARD') || (#id == #dto.id && hasPermission(#id, 'Committee', 'write'))")
    @PutMapping(value = ["/committees/{id}"])
    fun updateCommittee(
        @PathVariable id: Long,
        @Valid @RequestBody dto: @Valid AdvancedCommitteeDTO
    ): AdvancedCommitteeDTO {
        var committee = service.findById(id)
        advancedMapper.fromDTO(dto, committee)
        committee = service.update(committee)
        return advancedMapper.toDTO(committee)
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping(value = ["/committees/{id}"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCommitteeById(@PathVariable id: Long) {
        service.deleteById(id)
    }
}
