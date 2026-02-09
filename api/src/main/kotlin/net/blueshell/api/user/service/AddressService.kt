package net.blueshell.api.user.service

import net.blueshell.api.user.model.Address
import net.blueshell.api.user.repository.AddressRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.stereotype.Service

@Service
class AddressService(repository: AddressRepository) : BaseModelService<Address, Long, AddressRepository>(repository)