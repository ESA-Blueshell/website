package net.blueshell.api.domain.user.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.domain.user.command.*
import net.blueshell.api.domain.user.web.dto.AddressResponse
import net.blueshell.api.domain.user.web.dto.CreateAddressRequest
import net.blueshell.api.domain.user.web.dto.UpdateAddressRequest
import net.blueshell.api.domain.user.web.mapping.asResponse
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.web.BaseController
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Addresses")
class AddressController(
    service: net.blueshell.api.domain.user.application.AddressService,
    private val commandBus: CommandBus
) : BaseController<net.blueshell.api.domain.user.application.AddressService>(service) {
    @PostMapping("/users/{userId}/addresses")
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#userId, 'User', 'write')")
    @ResponseStatus(
        HttpStatus.CREATED
    )
    fun createAddress(@PathVariable userId: Long, @Valid @RequestBody request: CreateAddressRequest): AddressResponse {
        val address = commandBus.dispatch(
            CreateAddressCommand(
                userId = userId,
                country = requireNotNull(request.country) { "Country is required" },
                city = requireNotNull(request.city) { "City is required" },
                street = requireNotNull(request.street) { "Street is required" },
                houseNumber = requireNotNull(request.houseNumber) { "House number is required" },
                zipCode = requireNotNull(request.zipCode) { "Zip code is required" }
            )
        )
        return address.asResponse()
    }

    @PutMapping("/addresses/{id}")
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#id, 'Address', 'write')")
    fun updateAddress(@PathVariable id: Long, @Valid @RequestBody request: UpdateAddressRequest): AddressResponse {
        val address = commandBus.dispatch(
            UpdateAddressCommand(
                id = id,
                country = requireNotNull(request.country) { "Country is required" },
                city = requireNotNull(request.city) { "City is required" },
                street = requireNotNull(request.street) { "Street is required" },
                houseNumber = requireNotNull(request.houseNumber) { "House number is required" },
                zipCode = requireNotNull(request.zipCode) { "Zip code is required" },
                version = request.version
            )
        )
        return address.asResponse()
    }

    @GetMapping("/addresses")
    @PreAuthorize("hasAuthority('BOARD')")
    fun findAllAddresses(): MutableList<AddressResponse> {
        val addresses = commandBus.dispatch(FindAllAddressesCommand())
        return addresses.map { it.asResponse() }.toMutableList()
    }

    @GetMapping("/addresses/{id}")
    @PreAuthorize("hasAuthority('BOARD') || hasPermission(#id, 'Address', 'read')")
    fun findAddressById(@PathVariable id: Long): AddressResponse {
        val address = commandBus.dispatch(FindAddressByIdCommand(id))
        return address.asResponse()
    }

    @DeleteMapping("/users/{userId}/addresses")
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUserAddress(@PathVariable userId: Long) {
        commandBus.dispatch(DeleteUserAddressCommand(userId))
    }

    @DeleteMapping("/addresses/{id}")
    @PreAuthorize("hasAuthority('BOARD')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAddressById(@PathVariable id: Long) {
        commandBus.dispatch(DeleteAddressByIdCommand(id))
    }
}
