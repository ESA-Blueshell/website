package net.blueshell.api.platform.integration.contact.persistence.repository

import net.blueshell.api.platform.integration.contact.persistence.ContactListMembership
import net.blueshell.api.shared.repository.BaseRepository

interface ContactListMembershipRepository : BaseRepository<ContactListMembership, Long> {
    fun findByContactIdAndContactListId(contactId: Long, contactListId: Long): ContactListMembership?
    fun findAllByContactListId(contactListId: Long): List<ContactListMembership>
    fun findAllByContactId(contactId: Long): List<ContactListMembership>
}
