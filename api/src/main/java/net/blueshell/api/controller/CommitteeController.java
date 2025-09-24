package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.AdvancedController;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.base.BaseDTO;
import net.blueshell.api.dto.committee.AdvancedCommitteeDTO;
import net.blueshell.api.mapper.committee.AdvancedCommitteeMapper;
import net.blueshell.api.mapper.committee.SimpleCommitteeMapper;
import net.blueshell.api.model.Committee;
import net.blueshell.api.service.CommitteeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@Tag(name = "Committees")
public class CommitteeController extends AdvancedController<CommitteeService, AdvancedCommitteeMapper, SimpleCommitteeMapper> {

    @Autowired
    public CommitteeController(CommitteeService service, AdvancedCommitteeMapper advancedCommitteeMapper, SimpleCommitteeMapper simpleCommitteeMapper) {
        super(service, advancedCommitteeMapper, simpleCommitteeMapper);
    }

    @GetMapping("/committeeMembers/committees")
    @PermitAll
    public List<? extends BaseDTO> findCommitteesForCurrentUser() {
        if (hasAuthority(Role.BOARD)) {
            return advancedMapper.toDTOs(service.findAll());
        }

        List<Committee> committees = service.findAllByUserId(getPrincipal().getId());
        return advancedMapper.toDTOs(committees);
    }

    @GetMapping("/committees")
    @PermitAll
    public List<? extends BaseDTO> findCommittees() {
        if (hasAuthority(Role.BOARD)) {
            return advancedMapper.toDTOs(service.findAll());
        }

        return simpleMapper.toDTOs(service.findAll());
    }

    @PreAuthorize("hasPermission(#committeeId, 'Committee', 'read')")
    @GetMapping("/committees/{committeeId}")
    public BaseDTO findCommitteeById(
            @PathVariable("committeeId") Long committeeId
    ) {
        var committee = service.findById(committeeId);
        if (hasAuthority(Role.BOARD) || committee.hasMember(getPrincipal())) {
            return advancedMapper.toDTO(committee);
        }

        return simpleMapper.toDTO(committee);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/committees")
    public AdvancedCommitteeDTO createCommittee(@Valid @RequestBody AdvancedCommitteeDTO advancedCommitteeDTO) {
        var committee = advancedMapper.fromDTO(advancedCommitteeDTO);
        service.create(committee);
        return advancedMapper.toDTO(committee);
    }

    @PreAuthorize("hasAuthority('BOARD') || (#id == dto.id && hasPermission(#id, 'Committee', 'write'))")
    @PutMapping(value = "/committees/{id}")
    public BaseDTO updateCommittee(@PathVariable("id") Long id, @Valid @RequestBody AdvancedCommitteeDTO dto) {
        var committee = service.findById(id);
        advancedMapper.fromDTO(dto, committee);
        service.update(committee);
        return advancedMapper.toDTO(committee);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @DeleteMapping(value = "/committees/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCommitteeById(@PathVariable("id") Long id) {
        service.delete(id);
    }
}
