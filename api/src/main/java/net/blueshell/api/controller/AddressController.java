package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.dto.AddressDTO;
import net.blueshell.api.mapper.AddressMapper;
import net.blueshell.api.service.AddressService;
import net.blueshell.api.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Addresses")
public class AddressController extends BaseController<AddressService, AddressMapper> {

    private final UserService users;

    public AddressController(AddressService service, AddressMapper mapper, UserService users) {
        super(service, mapper);
        this.users = users;
    }

    @PostMapping("/users/{userId}/addresses")
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#userId, 'User', 'write')")
    @ResponseStatus(HttpStatus.CREATED)
    public AddressDTO createAddress(@PathVariable("userId") Long userId, @Valid @RequestBody AddressDTO dto) {
        var user = users.findById(userId);
        var address = mapper.fromDTO(dto);
        user.setAddress(address);
        users.update(user);
        return mapper.toDTO(address);
    }

    @PutMapping("/addresses/{id}")
    @PreAuthorize("hasAuthority('BOARD') || (#id == #dto.id && hasPermission(#id, 'Address', 'write'))")
    public AddressDTO updateAddress(@PathVariable("id") Long id, @Valid @RequestBody AddressDTO dto) {
        var address = service.findById(id);
        mapper.fromDTO(dto, address);
        service.update(address);
        return mapper.toDTO(address);
    }

    @GetMapping("/addresses")
    @PreAuthorize("hasAuthority('BOARD')")
    public List<AddressDTO> findAllAddresses() {
        var addresses = service.findAll();
        return mapper.toDTOs(addresses);
    }

    @GetMapping("/addresses/{id}")
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#id, 'Address', 'read')")
    public AddressDTO findAddressById(@PathVariable("id") Long id) {
        var address = service.findById(id);
        return mapper.toDTO(address);
    }

    @DeleteMapping("/addresses/{id}")
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddressById(@PathVariable("id") Long id) {
        service.deleteById(id);
    }
}