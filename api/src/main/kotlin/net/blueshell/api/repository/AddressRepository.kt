package net.blueshell.api.repository

import net.blueshell.api.model.Address
import net.blueshell.api.repository.base.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface AddressRepository : BaseRepository<Address, Long>