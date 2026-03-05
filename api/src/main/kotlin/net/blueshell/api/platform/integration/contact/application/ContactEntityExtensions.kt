package net.blueshell.api.platform.integration.contact.application

import net.blueshell.api.domain.user.application.contact.ContactSystem
import net.blueshell.api.platform.integration.contact.persistence.BrevoContact
import net.blueshell.api.platform.integration.contact.persistence.Contact
import net.blueshell.api.platform.integration.contact.persistence.ContactList
import net.blueshell.api.platform.integration.contact.persistence.ListmonkContact

/**
 * System-routing extensions for Contact and ContactList entities.
 *
 * These are the ONLY places where ContactSystem switching logic lives.
 * Job handlers call these extensions and never switch on system themselves.
 * Adding a new system only requires updating these when-expressions.
 */

fun Contact.externalId(system: ContactSystem): Long? = when (system) {
    ContactSystem.LISTMONK -> listmonkContact?.externalId
    ContactSystem.BREVO    -> brevoContact?.externalId
}

fun Contact.setExternalId(system: ContactSystem, id: Long) {
    when (system) {
        ContactSystem.LISTMONK -> listmonkContact = ListmonkContact(contact = this, externalId = id)
        ContactSystem.BREVO    -> brevoContact = BrevoContact(contact = this, externalId = id)
    }
}

fun ContactList.externalListId(system: ContactSystem): Long? = when (system) {
    ContactSystem.LISTMONK -> listmonkList?.externalId
    ContactSystem.BREVO    -> brevoList?.externalId
}
