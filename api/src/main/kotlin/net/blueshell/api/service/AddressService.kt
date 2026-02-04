package net.blueshell.api.service

import net.blueshell.api.base.BaseModelService
import net.blueshell.api.model.Address
import net.blueshell.api.repository.AddressRepository
import org.springframework.stereotype.Service

@Service
class AddressService(repository: AddressRepository) : BaseModelService<Address, Long, AddressRepository>(repository)