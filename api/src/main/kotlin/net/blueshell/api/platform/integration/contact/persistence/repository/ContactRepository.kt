package net.blueshell.api.platform.integration.contact.persistence.repository

import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.shared.repository.BaseRepository

interface ContactRepository : BaseRepository<Contact, Long> {
    fun findByUserId(userId: Long): Contact?
}
