package net.blueshell.api.controller;

import jakarta.validation.Valid;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.base.AdvancedController;
import net.blueshell.api.common.enums.Role;
import net.blueshell.api.dto.AdvancedCommitteeDTO;
import net.blueshell.api.dto.BaseDTO;
import net.blueshell.api.dto.BlogDTO;
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
public class CommitteeController extends AdvancedController<CommitteeService, AdvancedCommitteeMapper, SimpleCommitteeMapper> {

    @Autowired
    public CommitteeController(CommitteeService service, AdvancedCommitteeMapper advancedCommitteeMapper, SimpleCommitteeMapper simpleCommitteeMapper) {
        super(service, advancedCommitteeMapper, simpleCommitteeMapper);
    }

    @GetMapping("/committees")
    public List<? extends BaseDTO> getCommittees(@RequestParam(required = false, defaultValue = "false") boolean isMember) {
        if (hasAuthority(Role.BOARD)) {
            return advancedMapper.toDTOs(service.findAll());
        } else if (isMember) {
            List<Committee> committees = service.findALlByUserId(getPrincipal().getId());
            return advancedMapper.toDTOs(committees);
        }

        return simpleMapper.toDTOs(service.findAll());
    }

    @PreAuthorize("hasPermission(#committeeId, 'Committee', 'read')")
    @GetMapping("/committees/{committeeId}")
    public BaseDTO getCommitteeById(
            @PathVariable("committeeId") Long committeeId
    ) {
        Committee committee = service.findById(committeeId);
        if (hasAuthority(Role.BOARD) || committee.hasMember(getPrincipal())) {
            return advancedMapper.toDTO(committee);
        }

        return simpleMapper.toDTO(committee);
    }


    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/committees")
    public AdvancedCommitteeDTO createCommittee(@Valid @RequestBody AdvancedCommitteeDTO advancedCommitteeDTO) {
        Committee committee = advancedMapper.fromDTO(advancedCommitteeDTO);
        service.create(committee);
        return advancedMapper.toDTO(committee);
    }

    @PreAuthorize("hasPermission(#committeeId, 'Committee', 'write')")
    @PutMapping(value = "/committees/{committeeId}")
    public BaseDTO updateCommittee(
            @PathVariable("committeeId") Long committeeId,
            @Valid @RequestBody AdvancedCommitteeDTO dto) {
        dto.setId(committeeId);
        var committee = advancedMapper.fromDTO(dto);
        service.update(committee);
        return advancedMapper.toDTO(committee);
    }

    @PreAuthorize("hasPermission(#committeeId, 'Committee', 'delete')")
    @DeleteMapping(value = "/committees/{committeeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCommitteeById(@PathVariable("committeeId") Long committeeId) {
        service.delete(committeeId);
    }
}
