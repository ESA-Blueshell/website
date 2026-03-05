package net.blueshell.api.platform.integration.contact.persistence.repository

import net.blueshell.api.platform.integration.contact.persistence.ContactList
import net.blueshell.api.shared.repository.BaseRepository

interface ContactListRepository : BaseRepository<ContactList, Long> {
    fun findByName(name: String): ContactList?
}
