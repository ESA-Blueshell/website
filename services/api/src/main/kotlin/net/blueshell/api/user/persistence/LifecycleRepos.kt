package net.blueshell.api.user.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface AddressLifecycleRepo : BaseRepository<AddressLifecycle, Long>

@Repository
interface ProfileLifecycleRepo : BaseRepository<ProfileLifecycle, Long>
