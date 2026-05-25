package net.blueshell.api.platform.integration.sync.target.contact

import net.blueshell.api.platform.integration.contact.adapter.listmonk.ListmonkContactAdapter
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!test")
class ListmonkContactSyncTarget(adapter: ListmonkContactAdapter) :
    ContactAdapterSyncTarget(adapter, TargetSystem.LISTMONK)
