package net.blueshell.api.user.api

import net.blueshell.api.user.persistence.RoleChange
import net.blueshell.api.user.persistence.RoleChangeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** The recorded changes to somebody's roles, as another module reads them; only `user` writes one. */
@Service
class RoleChanges(
    private val repository: RoleChangeRepository,
) {
    /** The change recorded as [id]; a [NoSuchElementException] where there is none. */
    @Transactional(readOnly = true)
    fun find(id: Long): RoleChange = repository.findById(id).orElseThrow()
}
