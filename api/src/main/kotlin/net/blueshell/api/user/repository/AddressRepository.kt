package net.blueshell.api.user.repository

import net.blueshell.api.user.model.Address
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface AddressRepository : BaseRepository<Address, Long>