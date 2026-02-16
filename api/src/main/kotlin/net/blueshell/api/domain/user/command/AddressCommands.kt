package net.blueshell.api.domain.user.command

import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.user.persistence.Address
import net.blueshell.api.shared.command.Command

data class CreateAddressCommand(
    val userId: Long,
    val country: String,
    val city: String,
    val street: String,
    val houseNumber: String,
    val zipCode: String
) : Command<Address>

data class UpdateAddressCommand(
    val id: Long,
    val country: String,
    val city: String,
    val street: String,
    val houseNumber: String,
    val zipCode: String,
    @field:NotNull(message = "Version is required for optimistic locking")
    val version: Long
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
