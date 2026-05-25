package net.blueshell.api.platform.integration.sync.target.contact

import net.blueshell.api.platform.integration.contact.adapter.ContactData
import net.blueshell.api.platform.integration.contact.adapter.brevo.BrevoContactAdapter
import net.blueshell.api.platform.integration.sync.port.ContactSyncTarget
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!test & !dev")
class BrevoContactSyncTarget(
    private val adapter: BrevoContactAdapter,
) : ContactSyncTarget {
    override val system = TargetSystem.BREVO

    override fun push(aggregateId: Long, data: ContactData?, currentExternalId: String?): String? = when {
        data == null && currentExternalId == null -> null
        data == null -> { adapter.deleteContact(currentExternalId!!.toLong()); null }
        currentExternalId == null -> adapter.createContact(data).toString()
        else -> { adapter.updateContact(currentExternalId.toLong(), data); currentExternalId }
    }
}
