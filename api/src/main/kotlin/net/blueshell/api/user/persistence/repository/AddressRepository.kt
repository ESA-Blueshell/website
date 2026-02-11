package net.blueshell.api.user.persistence.repository

import net.blueshell.api.shared.repository.BaseRepository
import net.blueshell.api.user.persistence.Address
import org.springframework.stereotype.Repository

@Repository
interface AddressRepository : BaseRepository<Address, Long>