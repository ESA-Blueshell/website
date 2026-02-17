package net.blueshell.api.domain.user.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.domain.user.application.AddressService
import net.blueshell.api.domain.user.command.*
import net.blueshell.api.domain.user.web.dto.response.AddressResponse
import net.blueshell.api.domain.user.web.dto.request.CreateAddressRequest
import net.blueshell.api.domain.user.web.dto.request.UpdateAddressRequest
import net.blueshell.api.domain.user.web.mapping.request.asCommand
import net.blueshell.api.domain.user.web.mapping.response.asResponse
import net.blueshell.api.shared.command.CommandBus
import net.blueshell.api.shared.web.BaseController
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "Addresses")
class AddressController(
    service: AddressService,
    private val commandBus: CommandBus
) : BaseController<AddressService>(service) {
    @PostMapping("/addresses")
    @PreAuthorize("hasPermission(#request.userId, 'User', 'write')")
    @ResponseStatus(
        HttpStatus.CREATED
    )
    fun createAddress(@Valid @RequestBody request: CreateAddressRequest): AddressResponse {
        val address = commandBus.dispatch(request.asCommand())
        return address.asResponse()
    }

    @PutMapping("/addresses/{id}")
    @PreAuthorize("hasPermission(#id, 'Address', 'write')")
    fun updateAddress(@PathVariable id: Long, @Valid @RequestBody request: UpdateAddressRequest): AddressResponse {
        val address = commandBus.dispatch(request.asCommand(id))
        return address.asResponse()
    }

    @GetMapping("/addresses")
    @PreAuthorize("hasPermission(null, 'Address', 'read')")
    fun findAllAddresses(): MutableList<AddressResponse> {
        val addresses = commandBus.dispatch(FindAllAddressesCommand())
        return addresses.map { it.asResponse() }.toMutableList()
    }

    @GetMapping("/addresses/{id}")
    @PreAuthorize("hasPermission(#id, 'Address', 'read')")
    fun findAddressById(@PathVariable id: Long): AddressResponse {
        val address = commandBus.dispatch(FindAddressByIdCommand(id))
        return address.asResponse()
    }

    @DeleteMapping("/addresses/{id}")
    @PreAuthorize("hasPermission(#id, 'Address', 'delete')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAddressById(@PathVariable id: Long) {
        commandBus.dispatch(DeleteAddressByIdCommand(id))
    }
}
