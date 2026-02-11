package net.blueshell.api.domain.user.application

import net.blueshell.api.shared.service.BaseModelService
import net.blueshell.api.domain.user.persistence.Address
import net.blueshell.api.domain.user.persistence.repository.AddressRepository
import org.springframework.stereotype.Service

@Service
class AddressService(repository: AddressRepository) : BaseModelService<Address, Long, AddressRepository>(repository)