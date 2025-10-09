package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.dto.AddressDTO;
import net.blueshell.api.mapper.AddressMapper;
import net.blueshell.api.service.AddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/addresses")
@Tag(name = "Addresses")
public class AddressController extends BaseController<AddressService, AddressMapper> {

    @Autowired
    public AddressController(AddressService service, AddressMapper mapper) {
        super(service, mapper);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#dto.userId, 'User', 'write')")
    @ResponseStatus(HttpStatus.CREATED)
    public AddressDTO createAddress(@Valid @RequestBody AddressDTO dto) {
        var address = mapper.fromDTO(dto);
        address = service.create(address);
        return mapper.toDTO(address);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BOARD') || (#id == dto.id && hasPermission(#id, 'Address', 'write'))")
    public AddressDTO updateAddress(@PathVariable("id") Long id, @Valid @RequestBody AddressDTO dto) {
        var address = service.findById(id);
        mapper.fromDTO(dto, address);
        service.update(address);
        return mapper.toDTO(address);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BOARD')")
    public List<AddressDTO> findAllAddresses() {
        var addresses = service.findAll();
        return mapper.toDTOs(addresses);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#id, 'Address', 'read')")
    public AddressDTO findAddressById(@PathVariable("id") Long id) {
        var address = service.findById(id);
        return mapper.toDTO(address);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddressById(@PathVariable("id") Long id) {
        service.deleteById(id);
    }
}