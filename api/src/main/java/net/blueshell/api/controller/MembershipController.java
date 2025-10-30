package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.BadRequestException;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.controller.filter.MembershipFilter;
import net.blueshell.api.dto.MembershipDTO;
import net.blueshell.api.mapper.MembershipMapper;
import net.blueshell.api.model.Membership;
import net.blueshell.api.service.MembershipService;
import net.blueshell.api.validation.group.Administration;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
@Tag(name = "Memberships")
public class MembershipController extends BaseController<MembershipService, MembershipMapper> {

    public MembershipController(MembershipService service, MembershipMapper mapper) {
        super(service, mapper);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("/memberships")
    public List<MembershipDTO> findMemberships(@ParameterObject MembershipFilter filter) {
        return mapper.toDTOs(service.findByFilter(filter));
    }

    @PreAuthorize("hasAuthority('GUEST')")
    @PostMapping("/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    public MembershipDTO createMembership() {
        if (hasAuthority(Role.MEMBER)) {
            throw new AccessDeniedException("User is already a member");
        } else if (getPrincipal().getAddressId() == null) {
            throw new AccessDeniedException("User must have an address");
        }

        var membership = new Membership();
        membership.setUserId(getPrincipal().getId());
        service.create(membership);
        return mapper.toDTO(membership);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("memberships/member")
    @ResponseStatus(HttpStatus.CREATED)
    public MembershipDTO boardCreateMembership(@Validated(Administration.class) @RequestBody MembershipDTO dto
    ) {
        Membership membership = mapper.fromDTO(dto);
        membership = service.create(membership);
        return mapper.toDTO(membership);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PutMapping(value = "/{id}")
    public MembershipDTO updateMembership(@PathVariable("id") Long id, @RequestBody MembershipDTO dto) {
        var membership = service.findById(id);
        mapper.fromDTO(dto, membership);
        membership = service.update(membership);
        return mapper.toDTO(membership);
    }

    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#id, 'Membership', 'read')")
    @GetMapping(value = "/{id}")
    public MembershipDTO findMembershipById(@PathVariable("id") Long id) {
        return mapper.toDTO(service.findById(id));
    }
}
