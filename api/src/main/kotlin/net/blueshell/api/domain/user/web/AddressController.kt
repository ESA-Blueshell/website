package net.blueshell.api.domain.user.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.domain.user.application.AddressService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.web.dto.AddressResponse
import net.blueshell.api.domain.user.web.dto.CreateAddressRequest
import net.blueshell.api.domain.user.web.dto.UpdateAddressRequest
import net.blueshell.api.domain.user.web.mapping.asEntity
import net.blueshell.api.domain.user.web.mapping.asResponse
import net.blueshell.api.shared.web.BaseController
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Addresses")
class AddressController(service: AddressService, private val users: UserService) :
    BaseController<AddressService>(service) {
    @PostMapping("/users/{userId}/addresses")
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#userId, 'User', 'write')")
    @ResponseStatus(
        HttpStatus.CREATED
    )
    fun createAddress(@PathVariable userId: Long, @Valid @RequestBody request: CreateAddressRequest): AddressResponse {
        var user = users.findById(userId)
        val address = request.asEntity()
        user.address = address
        user = users.update(user)
        return user.address!!.asResponse()
    }

    @PutMapping("/addresses/{id}")
    @PreAuthorize("hasAuthority('BOARD') || (#id == #dto.id && hasPermission(#id, 'Address', 'write'))")
    fun updateAddress(@PathVariable id: Long, @Valid @RequestBody request: UpdateAddressRequest): AddressResponse {
        var address = service.findById(id)
        address = request.asEntity(address)
        address = service.update(address)
        return address.asResponse()
    }

    @GetMapping("/addresses")
    @PreAuthorize("hasAuthority('BOARD')")
    fun findAllAddresses(): MutableList<AddressResponse> {
        val addresses = service.findAll()
        return addresses.map { it.asResponse() }.toMutableList()
    }

    @GetMapping("/addresses/{id}")
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#id, 'Address', 'read')")
    fun findAddressById(@PathVariable id: Long): AddressResponse {
        val address = service.findById(id)
        return address.asResponse()
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
