package net.blueshell.api.platform.integration.mock

import net.blueshell.api.platform.integration.contact.adapter.ContactData
import net.blueshell.api.platform.integration.sync.port.ContactSyncTarget
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/** Test/dev contact target backed by [MockContactAdapter]'s in-memory store. */
@Component
@Primary
@Profile("test | dev")
class MockListmonkContactSyncTarget(
    private val adapter: MockContactAdapter,
) : ContactSyncTarget {
    override val system = TargetSystem.LISTMONK

    override fun push(aggregateId: Long, data: ContactData?, currentExternalId: String?): String? = when {
        data == null && currentExternalId == null -> null
        data == null -> { adapter.deleteContact(currentExternalId!!.toLong()); null }
        currentExternalId == null -> adapter.createContact(data).toString()
        else -> { adapter.updateContact(currentExternalId.toLong(), data); currentExternalId }
    }
}
