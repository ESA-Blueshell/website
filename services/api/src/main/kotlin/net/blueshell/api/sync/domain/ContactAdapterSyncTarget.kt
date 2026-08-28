package net.blueshell.api.sync.domain

import net.blueshell.api.contact.api.ContactAdapter
import net.blueshell.api.contact.api.ContactData
import net.blueshell.api.shared.enums.TargetSystem

/**
 * Base for any [ContactSyncTarget] that delegates to a [ContactAdapter].
 * Subclasses only declare the system tag and inject the right adapter.
 */
abstract class ContactAdapterSyncTarget(
    private val adapter: ContactAdapter,
    override val system: TargetSystem,
) : ContactSyncTarget {
    override fun push(aggregateId: Long, data: ContactData?, currentExternalId: String?): String? = when {
        data == null && currentExternalId == null -> null
        data == null -> { adapter.deleteContact(currentExternalId!!.toLong()); null }
        currentExternalId == null -> adapter.createContact(data).toString()
        else -> adapter.updateContact(currentExternalId.toLong(), data).toString()
    }
}
