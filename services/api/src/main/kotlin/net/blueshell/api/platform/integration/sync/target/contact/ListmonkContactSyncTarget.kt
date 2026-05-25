package net.blueshell.api.platform.integration.sync.target.contact

import net.blueshell.api.platform.integration.contact.adapter.ContactData
import net.blueshell.api.platform.integration.contact.adapter.listmonk.ListmonkContactAdapter
import net.blueshell.api.platform.integration.sync.port.ContactSyncTarget
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!test")
class ListmonkContactSyncTarget(
    private val adapter: ListmonkContactAdapter,
) : ContactSyncTarget {
    override val system = TargetSystem.LISTMONK

    override fun push(aggregateId: Long, data: ContactData?, currentExternalId: String?): String? = when {
        data == null && currentExternalId == null -> null
        data == null -> { adapter.deleteContact(currentExternalId!!.toLong()); null }
        currentExternalId == null -> adapter.createContact(data).toString()
        else -> { adapter.updateContact(currentExternalId.toLong(), data); currentExternalId }
    }
}
