package net.blueshell.api.feature.user.repository

import net.blueshell.api.feature.user.model.Address
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface AddressRepository : BaseRepository<Address, Long>