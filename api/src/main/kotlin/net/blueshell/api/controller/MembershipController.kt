package net.blueshell.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.common.enums.Role
import net.blueshell.api.controller.base.BaseController
import net.blueshell.api.dto.MembershipDTO
import net.blueshell.api.mapper.MembershipMapper
import net.blueshell.api.model.Membership
import net.blueshell.api.model.filter.MembershipFilter
import net.blueshell.api.service.MembershipService
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping
@Tag(name = "Memberships")
class MembershipController(service: MembershipService, mapper: MembershipMapper) :
    BaseController<MembershipService, MembershipMapper>(service, mapper) {
    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("/memberships")
    fun findMemberships(@ParameterObject filter: MembershipFilter): MutableList<MembershipDTO> {
        return mapper.toDTOs(service.findByFilter(filter))
    }

    @PreAuthorize("hasAuthority('GUEST')")
    @PostMapping("/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    fun createMembership(): MembershipDTO? {
        if (hasAuthority(Role.MEMBER)) {
            throw AccessDeniedException("User is already a member")
        } else if (principal?.addressId == null) {
            throw AccessDeniedException("User must have an address")
        }

        val membership = Membership()
        membership.userId = principal!!.id!!
        service.create(membership)
        return mapper.toDTO(membership)
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("memberships/member")
    @ResponseStatus(HttpStatus.CREATED)
    fun boardCreateMembership(
        @Validated(net.blueshell.api.validation.group.Administration::class) @RequestBody dto: MembershipDTO
    ): MembershipDTO? {
        var membership = mapper.fromDTO(dto)
        membership = service.create(membership)
        return mapper.toDTO(membership)
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PutMapping(value = ["/{id}"])
    fun updateMembership(@PathVariable id: Long, @RequestBody dto: MembershipDTO): MembershipDTO? {
        var membership = service.findById(id)
        mapper.fromDTO(dto, membership)
        membership = service.update(membership)
        return mapper.toDTO(membership)
    }

    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#id, 'Membership', 'read')")
    @GetMapping(value = ["/{id}"])
    fun findMembershipById(@PathVariable id: Long): MembershipDTO {
        return mapper.toDTO(service.findById(id))
    }
}
