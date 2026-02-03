package net.blueshell.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.base.BaseController
import net.blueshell.api.dto.AddressDTO
import net.blueshell.api.mapper.AddressMapper
import net.blueshell.api.service.AddressService
import net.blueshell.api.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Addresses")
class AddressController(service: AddressService, mapper: AddressMapper, private val users: UserService) :
    BaseController<AddressService, AddressMapper>(service, mapper) {
    @PostMapping("/users/{userId}/addresses")
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#userId, 'User', 'write')")
    @ResponseStatus(
        HttpStatus.CREATED
    )
    fun createAddress(@PathVariable userId: Long, @Valid @RequestBody dto: AddressDTO): AddressDTO {
        var user = users.findById(userId)
        val address = mapper.fromDTO(dto)
        user.address = address
        user = users.update(user)
        return mapper.toDTO(user.address!!)
    }

    @PutMapping("/addresses/{id}")
    @PreAuthorize("hasAuthority('BOARD') || (#id == #dto.id && hasPermission(#id, 'Address', 'write'))")
    fun updateAddress(@PathVariable id: Long, @Valid @RequestBody dto: AddressDTO): AddressDTO {
        var address = service.findById(id)
        mapper.fromDTO(dto, address)
        address = service.update(address)
        return mapper.toDTO(address)
    }

    @GetMapping("/addresses")
    @PreAuthorize("hasAuthority('BOARD')")
    fun findAllAddresses(): MutableList<AddressDTO> {
        val addresses = service.findAll()
        return mapper.toDTOs(addresses)
    }

    @GetMapping("/addresses/{id}")
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#id, 'Address', 'read')")
    fun findAddressById(@PathVariable id: Long): AddressDTO {
        val address = service.findById(id)
        return mapper.toDTO(address)
    }

    @DeleteMapping("/users/{userId}/addresses")
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUserAddress(@PathVariable userId: Long) {
        val user = users.findById(userId)
        user.address = null
        users.update(user)
    }

    @DeleteMapping("/addresses/{id}")
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAddressById(@PathVariable id: Long) {
        service.deleteById(id)
    }
}
