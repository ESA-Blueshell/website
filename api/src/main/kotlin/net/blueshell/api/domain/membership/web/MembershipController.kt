package net.blueshell.api.domain.membership.web

import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.domain.membership.application.MembershipService
import net.blueshell.api.domain.membership.persistence.Membership
import net.blueshell.api.domain.membership.persistence.filter.MembershipFilter
import net.blueshell.api.domain.membership.web.dto.MembershipDTO
import net.blueshell.api.domain.membership.web.mapping.asDto
import net.blueshell.api.domain.membership.web.mapping.asEntity
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.model.asRef
import net.blueshell.api.shared.web.BaseController
import net.blueshell.api.domain.user.persistence.User
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping
@Tag(name = "Memberships")
class MembershipController(service: MembershipService) : BaseController<MembershipService>(service) {
    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("/memberships")
    fun findMemberships(@ParameterObject filter: MembershipFilter): MutableList<MembershipDTO> {
        return service.findByFilter(filter).map { it.asDto() }.toMutableList()
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
        membership.user = User::class.asRef(principal!!.id!!)
        service.create(membership)
        return membership.asDto()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("memberships/member")
    @ResponseStatus(HttpStatus.CREATED)
    fun boardCreateMembership(
        @Validated(net.blueshell.api.shared.validation.group.Administration::class) @RequestBody dto: MembershipDTO
    ): MembershipDTO? {
        var membership = dto.asEntity()
        membership = service.create(membership)
        return membership.asDto()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PutMapping(value = ["/{id}"])
    fun updateMembership(@PathVariable id: Long, @RequestBody dto: MembershipDTO): MembershipDTO? {
        var membership = service.findById(id)
        dto.asEntity(membership)
        membership = service.update(membership)
        return membership.asDto()
    }

    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#id, 'Membership', 'read')")
    @GetMapping(value = ["/{id}"])
    fun findMembershipById(@PathVariable id: Long): MembershipDTO {
        return service.findById(id).asDto()
    }
}
