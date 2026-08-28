package net.blueshell.api.committee.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.validation.Valid
import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.security.SecurityUtils
import net.blueshell.api.shared.security.UserPrincipal
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
        val committees = if (includeAll) service.findAll() else service.findAllByUserId(principalId)
        return committees.map { it.asDetailResponse() }.toMutableList()
    }

    @GetMapping("/committees")
    @PermitAll
    fun findCommittees(): MutableList<CommitteeResponse> {
        val committees = service.findAll()
        // Taken from the security context rather than bound as a request parameter, so the
        // detail level is picked from a server-held value only.
        return if (SecurityUtils.hasAuthority(Role.BOARD)) {
            committees.map { it.asDetailResponse() }.toMutableList()
        } else {
            committees.map { it.asSummaryResponse() }.toMutableList()
        }
    }

    @PreAuthorize("hasPermission(#committeeId, 'Committee', 'read')")
    @GetMapping("/committees/{committeeId}")
    fun findCommitteeById(@PathVariable committeeId: Long): CommitteeResponse {
        val committee = service.findById(committeeId)
        // Taken from the security context rather than bound as a request parameter, so the
        // detail level is picked from a server-held value only.
        val principal = SecurityUtils.currentPrincipal()
        if (principal?.hasAuthority(Role.BOARD) == true || committee.hasMember(principal?.id)) {
            return committee.asDetailResponse()
        }

        return committee.asSummaryResponse()
    }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'Committee', 'write')")
    @PostMapping("/committees")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCommittee(@Valid @RequestBody request: @Valid CreateCommitteeRequest): CommitteeDetailResponse {
        val committee = service.createWithMembers(
            name = request.name,
            description = request.description,
            members = request.members.map { it.asData() }.toMutableList(),
        )
        return committee.asDetailResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Committee', 'write')")
    @PutMapping(value = ["/committees/{id}"])
    fun updateCommittee(
        @PathVariable id: Long,
        @Valid @RequestBody request: @Valid UpdateCommitteeRequest
    ): CommitteeDetailResponse {
        val committee = service.updateWithMembers(
            id = id,
            name = request.name,
            description = request.description,
            members = request.members.map { it.asData() }.toMutableList(),
            version = request.version,
        )
        return committee.asDetailResponse()
    }

    @PreAuthorize("hasPermission(#id, 'Committee', 'delete')")
    @DeleteMapping(value = ["/committees/{id}"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCommitteeById(@PathVariable id: Long) {
        service.deleteById(id)
    }
}
