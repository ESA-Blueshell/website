package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.user.application.AddressService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.command.*
import net.blueshell.api.domain.user.persistence.Address
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class CreateAddressHandler(
    private val users: UserService
) : CommandHandler<CreateAddressCommand, Address> {
    override val commandType = CreateAddressCommand::class

    override fun handle(command: CreateAddressCommand): Address {
        val user = users.findById(command.userId)
        val address = Address(
            user = user,
            country = command.country,
            city = command.city,
            street = command.street,
            houseNumber = command.houseNumber,
            zipCode = command.zipCode
        )

        user.replaceAddress(address)
        val updated = users.update(user)
        return checkNotNull(updated.address) { "Address was not linked to user ${user.id}" }
    }
}

@Component
class UpdateAddressHandler(
    private val addressService: AddressService
) : CommandHandler<UpdateAddressCommand, Address> {
    override val commandType = UpdateAddressCommand::class

    override fun handle(command: UpdateAddressCommand): Address {
        val address = addressService.findById(command.id).apply {
            country = command.country
            city = command.city
            street = command.street
            houseNumber = command.houseNumber
            zipCode = command.zipCode
            version = command.version
        }
        return addressService.update(address)
    }
}

@Component
class FindAllAddressesHandler(
    private val addressService: AddressService
) : CommandHandler<FindAllAddressesCommand, MutableList<Address>> {
    override val commandType = FindAllAddressesCommand::class

    override fun handle(command: FindAllAddressesCommand): MutableList<Address> {
        return addressService.findAll()
    }
}

@Component
class FindAddressByIdHandler(
    private val addressService: AddressService
) : CommandHandler<FindAddressByIdCommand, Address> {
    override val commandType = FindAddressByIdCommand::class

    override fun handle(command: FindAddressByIdCommand): Address {
        return addressService.findById(command.id)
    }
}

@Component
class DeleteAddressByIdHandler(
    private val addressService: AddressService
) : CommandHandler<DeleteAddressByIdCommand, Unit> {
    override val commandType = DeleteAddressByIdCommand::class

    override fun handle(command: DeleteAddressByIdCommand) {
        addressService.deleteById(command.id)
    }
}
