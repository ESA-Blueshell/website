package net.blueshell.api.contact.persistence

import net.blueshell.api.shared.repository.BaseRepository

interface ContactListRepository : BaseRepository<ContactList, Long> {
    fun findByName(name: String): ContactList?
}
