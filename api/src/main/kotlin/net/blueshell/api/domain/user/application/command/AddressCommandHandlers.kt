package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.user.application.AddressService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.command.CreateAddressCommand
import net.blueshell.api.domain.user.command.DeleteAddressByIdCommand
import net.blueshell.api.domain.user.command.DeleteUserAddressCommand
import net.blueshell.api.domain.user.command.FindAddressByIdCommand
import net.blueshell.api.domain.user.command.FindAllAddressesCommand
import net.blueshell.api.domain.user.command.UpdateAddressCommand
import net.blueshell.api.domain.user.persistence.Address
import net.blueshell.api.domain.user.web.mapping.asEntity
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class CreateAddressHandler(
    private val users: UserService
) : CommandHandler<CreateAddressCommand, Address> {
    override val commandType = CreateAddressCommand::class

    override fun handle(command: CreateAddressCommand): Address {
        var user = users.findById(command.userId)
        val address = command.dto.asEntity()
        user.address = address
        user = users.update(user)
        return user.address!!
    }
}

@Component
class UpdateAddressHandler(
    private val addressService: AddressService
) : CommandHandler<UpdateAddressCommand, Address> {
    override val commandType = UpdateAddressCommand::class

    override fun handle(command: UpdateAddressCommand): Address {
        var address = addressService.findById(command.id)
        address = command.dto.asEntity(address)
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
class DeleteUserAddressHandler(
    private val users: UserService
) : CommandHandler<DeleteUserAddressCommand, Unit> {
    override val commandType = DeleteUserAddressCommand::class

    override fun handle(command: DeleteUserAddressCommand) {
        val user = users.findById(command.userId)
        user.address = null
        users.update(user)
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
