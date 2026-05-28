package net.blueshell.api.platform.integration.sync.target.contact

import net.blueshell.api.platform.integration.contact.adapter.ContactAdapter
import net.blueshell.api.platform.integration.contact.adapter.ContactData
import net.blueshell.api.platform.integration.sync.port.ContactSyncTarget
import net.blueshell.api.platform.integration.sync.port.TargetSystem

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
