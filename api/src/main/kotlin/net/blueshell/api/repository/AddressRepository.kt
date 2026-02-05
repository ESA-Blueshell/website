package net.blueshell.api.repository

import net.blueshell.api.repository.base.BaseRepository
import net.blueshell.api.model.Address
import org.springframework.stereotype.Repository

@Repository
interface AddressRepository : BaseRepository<Address, Long>