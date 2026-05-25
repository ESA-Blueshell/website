package net.blueshell.api.platform.integration.mock

import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.platform.integration.sync.target.contact.ContactAdapterSyncTarget
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Primary
@Profile("test | dev")
class MockListmonkContactSyncTarget(adapter: MockContactAdapter) :
    ContactAdapterSyncTarget(adapter, TargetSystem.LISTMONK)
