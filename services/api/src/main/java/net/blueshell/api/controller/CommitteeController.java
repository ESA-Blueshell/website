package net.blueshell.api.controller;

import jakarta.validation.Valid;
import jakarta.ws.rs.NotFoundException;
import net.blueshell.api.base.AdvancedController;
import net.blueshell.api.base.DTO;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.committee.AdvancedCommitteeDTO;
import net.blueshell.api.mapping.committee.AdvancedCommitteeMapper;
import net.blueshell.api.mapping.committee.SimpleCommitteeMapper;
import net.blueshell.api.model.Committee;
import net.blueshell.api.service.CommitteeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
public class CommitteeController extends AdvancedController<CommitteeService, AdvancedCommitteeMapper, SimpleCommitteeMapper> {

    @Autowired
    public CommitteeController(CommitteeService service, AdvancedCommitteeMapper advancedCommitteeMapper, SimpleCommitteeMapper simpleCommitteeMapper) {
        super(service, advancedCommitteeMapper, simpleCommitteeMapper);
    }

    @GetMapping("/committees")
    public List<? extends DTO> getCommittees(@RequestParam(required = false) boolean isMember) {
        if (getPrincipal() != null && getPrincipal().hasRole(Role.BOARD)) {
            return advancedMapper.toDTOs(service.findAll());
        } else if (isMember) {
            return advancedMapper.toDTOs(service.findAllById(new ArrayList<>(getPrincipal().getCommitteeIds())));
        }

        return simpleMapper.toDTOs(service.findAll());
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/committees")
    public DTO createCommittee(@Valid @RequestBody AdvancedCommitteeDTO advancedCommitteeDTO) {
        Committee committee = advancedMapper.fromDTO(advancedCommitteeDTO);
        System.out.println("committee: " + committee);
        service.createCommittee(committee);
        return advancedMapper.toDTO(committee);
    }

    @PreAuthorize("hasPermission(#committeeId, 'Committee', 'write')")
    @PutMapping(value = "/committees/{committeeId}")
    public DTO updateCommittee(
            @PathVariable("committeeId") Long committeeId,
            @Valid @RequestBody AdvancedCommitteeDTO advancedCommitteeDTO) {
        Committee oldCommittee = service.findById(committeeId);

        if (!oldCommittee.hasMember(getPrincipal()) && !hasAuthority(Role.BOARD)) {
            throw new NotFoundException();
        }

        advancedCommitteeDTO.setId(committeeId);
        Committee newCommittee = advancedMapper.fromDTO(advancedCommitteeDTO);
        System.out.println("newCommittee: " + newCommittee);
        service.update(newCommittee);
        return advancedMapper.toDTO(newCommittee);
    }

    @PreAuthorize("hasPermission(#committeeId, 'Committee', 'delete')")
    @DeleteMapping(value = "/committees/{committeeId}")
    public void deleteCommitteeById(@PathVariable("committeeId") Long committeeId) {
        service.deleteById(committeeId);
    }
}
