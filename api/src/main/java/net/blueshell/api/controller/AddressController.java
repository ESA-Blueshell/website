
package net.blueshell.api.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.dto.AddressDTO;
import net.blueshell.api.mapper.AddressMapper;
import net.blueshell.api.model.Address;
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
        Address address = mapper.fromDTO(dto);
        service.create(address);
        return mapper.toDTO(address);
    }

    @PutMapping("/{addressId}")
    @PreAuthorize("hasAuthority('BOARD') || (#addressId.equals(dto.id) && hasPermission(#addressId, 'Address', 'write'))")
    public AddressDTO updateAddress(@PathVariable("addressId") Long addressId, @Valid @RequestBody AddressDTO dto) {
        dto.setId(addressId);
        Address address = mapper.fromDTO(dto);
        service.update(address);
        return mapper.toDTO(address);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BOARD')")
    public List<AddressDTO> findAllAddresses() {
        List<Address> addresses = service.findAll();
        return mapper.toDTOs(addresses);
    }

    @GetMapping("/{addressId}")
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#addressId, 'Address', 'read')")
    public AddressDTO findAddressById(@PathVariable("addressId") Long addressId) {
        Address address = service.findById(addressId);
        return mapper.toDTO(address);
    }

    @DeleteMapping("/{addressId}")
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(@PathVariable("addressId") Long addressId) {
        service.delete(addressId);
    }
}