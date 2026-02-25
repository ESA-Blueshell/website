package net.blueshell.api.domain.user.persistence.repository

import net.blueshell.api.domain.user.persistence.lifecycle.AddressLifecycle
import net.blueshell.api.domain.user.persistence.lifecycle.ProfileLifecycle
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface AddressLifecycleRepo : BaseRepository<AddressLifecycle, Long>

@Repository
interface ProfileLifecycleRepo : BaseRepository<ProfileLifecycle, Long>
