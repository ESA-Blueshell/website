package net.blueshell.api.domain.user.command

import net.blueshell.api.domain.user.persistence.Address
import net.blueshell.api.domain.user.web.dto.AddressDTO
import net.blueshell.api.shared.command.Command

data class CreateAddressCommand(
    val userId: Long,
    val dto: AddressDTO
) : Command<Address>

data class UpdateAddressCommand(
    val id: Long,
    val dto: AddressDTO
) : Command<Address>

class FindAllAddressesCommand : Command<MutableList<Address>>

data class FindAddressByIdCommand(
    val id: Long
) : Command<Address>

data class DeleteUserAddressCommand(
    val userId: Long
) : Command<Unit>

data class DeleteAddressByIdCommand(
    val id: Long
) : Command<Unit>
