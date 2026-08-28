package net.blueshell.api.user.domain

import net.blueshell.api.user.persistence.Address
import net.blueshell.api.user.persistence.AddressRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.stereotype.Service

@Service
class AddressService(repository: AddressRepository) : BaseModelService<Address, Long, AddressRepository>(repository)