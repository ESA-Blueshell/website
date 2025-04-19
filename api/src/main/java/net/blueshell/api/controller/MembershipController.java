package net.blueshell.api.controller;

import jakarta.validation.Valid;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.dto.MembershipDTO;
import net.blueshell.api.mapper.MembershipMapper;
import net.blueshell.api.model.Membership;
import net.blueshell.api.service.MembershipService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/memberships")
public class MembershipController extends BaseController<MembershipService, MembershipMapper> {

    public MembershipController(MembershipService service, MembershipMapper mapper) {
        super(service, mapper);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping()
    public List<MembershipDTO> getMemberships() {
        return mapper.toDTOs(service.findAll());
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping()
    public MembershipDTO createMembership(@Valid @RequestBody MembershipDTO dto) {
        Membership membership = mapper.fromDTO(dto);
        service.create(membership);
        return mapper.toDTO(membership);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PutMapping(value = "/{id}")
    public MembershipDTO updateMembership(@PathVariable("id") Long id, @RequestBody MembershipDTO dto) {
        service.findById(id);
        Membership membership = mapper.fromDTO(dto);
        service.update(membership);
        return mapper.toDTO(membership);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping(value = "/{id}")
    public MembershipDTO getMembershipById(@PathVariable("id") Long id) {
        return mapper.toDTO(service.findById(id));
    }
}
